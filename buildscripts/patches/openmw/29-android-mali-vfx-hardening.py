#!/usr/bin/env python3
"""ArenaMW Android: Mali/NG-GL4ES stability for spell VFX and particle systems.

Patch 28 removed the CPU-side crashes around magic VFX (use-after-delete on
summon despawn, missing/malformed VFX records, an Animation* kept across a
teleport). This patch targets the remaining GPU-side failure mode reported on
Mali parts: a hard crash while casting, and more generally in scenes carrying
many particle systems at once.

What it changes
---------------
1. Throwaway VFX subgraphs stop using vertex buffer objects and display lists.

   Spell / hit / summon VFX are created and destroyed continuously while
   casting. With OSG_VERTEX_BUFFER_HINT=VBO every one of those tiny drawables
   allocates a GL buffer and frees it a second later. NG-GL4ES forwards that to
   GLES, and a Mali tiler may still have the buffer referenced by a queued tile
   job when glDeleteBuffers arrives. That is the classic "crashes when there are
   a lot of particles" signature. VFX geometry is a handful of quads, so client
   arrays cost nothing here and remove the create/delete churn entirely.

2. Particle systems inside those subgraphs get setFreezeOnCull(true), so
   effects the camera cannot see stop being simulated and re-uploaded.

3. Optional spawn burst limiter for free VFX (ARENAMW_VFX_BURST, default off).
   An AoE spell hitting several actors spawns one effect per target in a single
   frame. Capping new free VFX per 200 ms is meant as an A/B diagnostic: if the
   crash disappears at a low cap, the trigger is simultaneous effect count
   rather than any one particular effect.

Runtime switches (set by GameActivity, overridable from the launcher env box):
    ARENAMW_VFX_SAFE=0     disable 1 and 2 entirely
    ARENAMW_VFX_BURST=<n>  enable 3 (0 or unset = off)

Anchoring
---------
The effectmanager.cpp anchors are text that patch 28 itself produces, so they
are exact by construction. The animation.cpp half depends on upstream text; if
its include anchor is not found, that half is skipped as a whole -- never
applied partially -- and the build continues. Nothing is fuzz-matched, and a
marker string (not the anchors) decides whether work has already been done, so
re-running on a patched tree is a no-op.
"""

from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: 29-android-mali-vfx-hardening.py <ArenaMW source dir>')

root = Path(sys.argv[1]).resolve()

MARKER = 'ArenaVfxHardeningVisitor'


def load(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        raise SystemExit(f'missing required source file: {rel}')
    return path.read_text(encoding='utf-8')


def save(rel: str, text: str) -> None:
    (root / rel).write_text(text, encoding='utf-8')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    """Exact single-occurrence replacement. Never fuzzy, never guessed."""
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one anchor, found {count}')
    return text.replace(old, new, 1)


def find_once(text: str, old: str) -> bool:
    return text.count(old) == 1


# --------------------------------------------------------------------------
# Shared C++ snippet
# --------------------------------------------------------------------------

VFX_HELPER = '''
namespace
{
    // ArenaMW Android (patch 29): hardening for short-lived VFX subgraphs.
    //
    // These drawables live for a second or two and are then thrown away. Keeping
    // them on client arrays avoids a constant stream of tiny glGenBuffers /
    // glDeleteBuffers pairs, which is what NG-GL4ES ends up issuing on Mali and
    // which is unsafe while the tiler may still reference the buffer.
    class ArenaVfxHardeningVisitor : public osg::NodeVisitor
    {
    public:
        ArenaVfxHardeningVisitor()
            : osg::NodeVisitor(osg::NodeVisitor::TRAVERSE_ALL_CHILDREN)
        {
            setNodeMaskOverride(~0u);
        }

        void apply(osg::Drawable& drawable) override
        {
            drawable.setUseDisplayList(false);
            drawable.setUseVertexBufferObjects(false);
            traverse(drawable);
        }
    };

    bool arenaVfxSafeEnabled()
    {
        static const bool enabled = []() {
            const char* value = std::getenv("ARENAMW_VFX_SAFE");
            return value == nullptr || value[0] != '0';
        }();
        return enabled;
    }
}
'''

EM_EXTRA_HELPER = '''
namespace
{
    // Particle systems keep simulating while culled by default. Freezing them
    // removes both the CPU update and the per-frame vertex re-upload for effects
    // the player cannot see, which is most of them in a busy fight.
    class ArenaParticleFreezeVisitor : public osg::NodeVisitor
    {
    public:
        ArenaParticleFreezeVisitor()
            : osg::NodeVisitor(osg::NodeVisitor::TRAVERSE_ALL_CHILDREN)
        {
            setNodeMaskOverride(~0u);
        }

        void apply(osg::Drawable& drawable) override
        {
            if (osgParticle::ParticleSystem* ps = dynamic_cast<osgParticle::ParticleSystem*>(&drawable))
                ps->setFreezeOnCull(true);
            traverse(drawable);
        }
    };

    // Optional diagnostic: cap how many new free VFX may spawn per time window.
    // Returns true when the caller should skip this effect.
    bool arenaVfxBurstExceeded()
    {
        static const int limit = []() {
            const char* value = std::getenv("ARENAMW_VFX_BURST");
            if (value == nullptr)
                return 0;
            try
            {
                return std::stoi(value);
            }
            catch (const std::exception&)
            {
                return 0;
            }
        }();

        if (limit <= 0)
            return false;

        using Clock = std::chrono::steady_clock;
        static Clock::time_point windowStart = Clock::now();
        static int spawnedInWindow = 0;

        const Clock::time_point now = Clock::now();
        if (std::chrono::duration_cast<std::chrono::milliseconds>(now - windowStart).count() >= 200)
        {
            windowStart = now;
            spawnedInWindow = 0;
        }

        if (spawnedInWindow >= limit)
            return true;

        ++spawnedInWindow;
        return false;
    }
}
'''


# --------------------------------------------------------------------------
# 1) Free / world VFX: apps/openmw/mwrender/effectmanager.cpp   (required)
# --------------------------------------------------------------------------

rel = 'apps/openmw/mwrender/effectmanager.cpp'
text = load(rel)

if MARKER in text:
    print('==> patch 29: effectmanager.cpp already hardened')
else:
    text = replace_once(
        text,
        '#include <exception>\n',
        '#include <exception>\n'
        '#include <chrono>\n'
        '#include <cstdlib>\n'
        '#include <string>\n'
        '\n'
        '#include <osg/Drawable>\n'
        '#include <osg/NodeVisitor>\n'
        '#include <osgParticle/ParticleSystem>\n',
        'effectmanager.cpp patch-29 includes',
    )

    text = replace_once(
        text,
        'void EffectManager::addEffect(const std::string &model, const std::string& textureOverride,'
        ' const osg::Vec3f &worldPosition, float scale, bool isMagicVFX)\n'
        '{\n'
        '    if (model.empty() || !mParentNode || !mResourceSystem)\n'
        '        return;\n',
        VFX_HELPER + EM_EXTRA_HELPER +
        '\nvoid EffectManager::addEffect(const std::string &model, const std::string& textureOverride,'
        ' const osg::Vec3f &worldPosition, float scale, bool isMagicVFX)\n'
        '{\n'
        '    if (model.empty() || !mParentNode || !mResourceSystem)\n'
        '        return;\n'
        '\n'
        '    // Diagnostic burst cap. Inactive unless ARENAMW_VFX_BURST is set.\n'
        '    if (arenaVfxBurstExceeded())\n'
        '        return;\n',
        'effectmanager.cpp patch-29 helpers and burst cap',
    )

    text = replace_once(
        text,
        '    if (!node)\n'
        '    {\n'
        '        Log(Debug::Warning) << "Skipping free VFX \'" << model << "\': scene instance is null";\n'
        '        return;\n'
        '    }\n'
        '\n'
        '    node->setNodeMask(Mask_Effect);\n',
        '    if (!node)\n'
        '    {\n'
        '        Log(Debug::Warning) << "Skipping free VFX \'" << model << "\': scene instance is null";\n'
        '        return;\n'
        '    }\n'
        '\n'
        '    if (arenaVfxSafeEnabled())\n'
        '    {\n'
        '        // Mali/NG-GL4ES: keep transient effect geometry off the VBO path and\n'
        '        // stop simulating particle systems the camera cannot see.\n'
        '        ArenaVfxHardeningVisitor hardening;\n'
        '        node->accept(hardening);\n'
        '        ArenaParticleFreezeVisitor freeze;\n'
        '        node->accept(freeze);\n'
        '    }\n'
        '\n'
        '    node->setNodeMask(Mask_Effect);\n',
        'effectmanager.cpp patch-29 free VFX hardening',
    )

    save(rel, text)
    print('==> patch 29: effectmanager.cpp hardened')


# --------------------------------------------------------------------------
# 2) Attached (actor) VFX: apps/openmw/mwrender/animation.cpp   (best effort)
#    Applied as a unit: if any anchor is missing, the file is left untouched.
# --------------------------------------------------------------------------

rel = 'apps/openmw/mwrender/animation.cpp'
text = load(rel)

INCLUDE_ANCHOR = '#include "animation.hpp"\n'
DECL_ANCHOR = (
    '    void Animation::addEffect (const std::string& model, int effectId, bool loop,'
    ' const std::string& bonename, const std::string& texture)\n'
    '    {\n'
)
NODE_ANCHOR = (
    '        if (!node)\n'
    '        {\n'
    '            parentNode->removeChild(trans);\n'
    '            Log(Debug::Warning) << "Skipping VFX \'" << model << "\': scene instance is null";\n'
    '            return;\n'
    '        }\n'
    '        node->getOrCreateStateSet()->setMode(GL_LIGHTING, osg::StateAttribute::OFF);\n'
)

if MARKER in text:
    print('==> patch 29: animation.cpp already hardened')
elif not (find_once(text, INCLUDE_ANCHOR) and find_once(text, DECL_ANCHOR) and find_once(text, NODE_ANCHOR)):
    print('   !! patch 29: animation.cpp anchors not unique; attached VFX left at stock')
    print('   !! (free/world VFX hardening in effectmanager.cpp is still active)')
else:
    text = replace_once(
        text,
        INCLUDE_ANCHOR,
        '#include "animation.hpp"\n'
        '\n'
        '#include <cstdlib>\n'
        '\n'
        '#include <osg/Drawable>\n'
        '#include <osg/NodeVisitor>\n',
        'animation.cpp patch-29 includes',
    )
    text = replace_once(text, DECL_ANCHOR, VFX_HELPER + '\n' + DECL_ANCHOR,
                        'animation.cpp patch-29 helper')
    text = replace_once(
        text,
        NODE_ANCHOR,
        '        if (!node)\n'
        '        {\n'
        '            parentNode->removeChild(trans);\n'
        '            Log(Debug::Warning) << "Skipping VFX \'" << model << "\': scene instance is null";\n'
        '            return;\n'
        '        }\n'
        '\n'
        '        if (arenaVfxSafeEnabled())\n'
        '        {\n'
        '            // Same reasoning as EffectManager: an attached spell effect is a\n'
        '            // throwaway subgraph and must not churn GL buffers on Mali.\n'
        '            ArenaVfxHardeningVisitor hardening;\n'
        '            node->accept(hardening);\n'
        '        }\n'
        '\n'
        '        node->getOrCreateStateSet()->setMode(GL_LIGHTING, osg::StateAttribute::OFF);\n',
        'animation.cpp patch-29 attached VFX hardening',
    )
    save(rel, text)
    print('==> patch 29: animation.cpp hardened')

print('ArenaMW Android Mali VFX hardening patch applied')
