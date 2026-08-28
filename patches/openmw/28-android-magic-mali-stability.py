#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: 28-android-magic-mali-stability.py <ArenaMW source dir>')

root = Path(sys.argv[1]).resolve()


def load(rel: str) -> str:
    path = root / rel
    if not path.is_file():
        raise SystemExit(f'missing required source file: {rel}')
    return path.read_text(encoding='utf-8')


def save(rel: str, text: str) -> None:
    (root / rel).write_text(text, encoding='utf-8')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    # Prefer the old anchor whenever it is still present.  Checking for the
    # replacement first is unsafe for short/common snippets because an identical
    # replacement may legitimately exist elsewhere in the same translation unit.
    count = text.count(old)
    if count == 1:
        return text.replace(old, new, 1)
    if count == 0 and new in text:
        return text
    raise SystemExit(f'{label}: expected exactly one anchor, found {count}')


# 1) Summon despawn: never read a Ptr after World::deleteObject().
#    The old order was a real use-after-delete and was especially timing-sensitive
#    when the renderer still referenced the actor/VFX on Android.
rel = 'apps/openmw/mwmechanics/actors.cpp'
text = load(rel)
old = '''        if (!ptr.isEmpty())
        {
            MWBase::Environment::get().getWorld()->deleteObject(ptr);

            const ESM::Static* fx = MWBase::Environment::get().getWorld()->getStore().get<ESM::Static>()
                    .search("VFX_Summon_End");
            if (fx)
                MWBase::Environment::get().getWorld()->spawnEffect("meshes\\\\" + fx->mModel,
                    "", ptr.getRefData().getPosition().asVec3());
            // Remove the summoned creature's summoned creatures as well
            MWMechanics::CreatureStats& stats = ptr.getClass().getCreatureStats(ptr);
            std::map<ESM::SummonKey, int>& creatureMap = stats.getSummonedCreatureMap();
            for (const auto& creature : creatureMap)
                cleanupSummonedCreature(stats, creature.second);
            creatureMap.clear();
        }
'''
new = '''        if (!ptr.isEmpty())
        {
            // Cache everything that is needed while the actor is still alive.
            // World::deleteObject() removes an active object from the scene and
            // the Ptr must not be dereferenced afterwards.
            const osg::Vec3f despawnPosition = ptr.getRefData().getPosition().asVec3();

            // Remove the summoned creature's summoned creatures before deleting
            // their parent so nested CreatureStats references remain valid.
            MWMechanics::CreatureStats& stats = ptr.getClass().getCreatureStats(ptr);
            std::map<ESM::SummonKey, int>& creatureMap = stats.getSummonedCreatureMap();
            for (const auto& creature : creatureMap)
                cleanupSummonedCreature(stats, creature.second);
            creatureMap.clear();

            const ESM::Static* fx = MWBase::Environment::get().getWorld()->getStore().get<ESM::Static>()
                    .search("VFX_Summon_End");
            if (fx)
                MWBase::Environment::get().getWorld()->spawnEffect("meshes\\\\" + fx->mModel,
                    "", despawnPosition);

            // Delete last. No ptr access is allowed below this point.
            MWBase::Environment::get().getWorld()->deleteObject(ptr);
        }
'''
text = replace_once(text, old, new, 'actors.cpp summon lifetime')
save(rel, text)


# 2) Spell hit VFX: missing/modded VFX records must not throw or dereference null.
#    Teleport VFX must re-fetch Animation after the world/cell transition.
rel = 'apps/openmw/mwmechanics/spellcasting.cpp'
text = load(rel)
old = '''                    const ESM::Static* castStatic;
                    if (!magicEffect->mHit.empty())
                        castStatic = MWBase::Environment::get().getWorld()->getStore().get<ESM::Static>().find (magicEffect->mHit);
                    else
                        castStatic = MWBase::Environment::get().getWorld()->getStore().get<ESM::Static>().find ("VFX_DefaultHit");
                    bool loop = (magicEffect->mData.mFlags & ESM::MagicEffect::ContinuousVfx) != 0;
                    // Note: in case of non actor, a free effect should be fine as well
                    MWRender::Animation* anim = MWBase::Environment::get().getWorld()->getAnimation(target);
                    if (anim && !castStatic->mModel.empty())
                        anim->addEffect("meshes\\\\" + castStatic->mModel, magicEffect->mIndex, loop, "", magicEffect->mParticle);
'''
new = '''                    const ESM::Static* castStatic = nullptr;
                    if (!magicEffect->mHit.empty())
                        castStatic = MWBase::Environment::get().getWorld()->getStore().get<ESM::Static>().search(magicEffect->mHit);
                    else
                        castStatic = MWBase::Environment::get().getWorld()->getStore().get<ESM::Static>().search("VFX_DefaultHit");
                    bool loop = (magicEffect->mData.mFlags & ESM::MagicEffect::ContinuousVfx) != 0;
                    // A malformed/missing VFX record is non-fatal. Skip only that visual.
                    MWRender::Animation* anim = MWBase::Environment::get().getWorld()->getAnimation(target);
                    if (anim && castStatic && !castStatic->mModel.empty())
                        anim->addEffect("meshes\\\\" + castStatic->mModel, magicEffect->mIndex, loop, "", magicEffect->mParticle);
'''
text = replace_once(text, old, new, 'spellcasting.cpp hit VFX guard')
text = replace_once(
    text,
    '            MWRender::Animation* anim = MWBase::Environment::get().getWorld()->getAnimation(mCaster);\n',
    '            MWRender::Animation* anim = MWBase::Environment::get().getWorld()->getAnimation(target);\n',
    'spellcasting.cpp teleport initial animation target',
)
old = '''                MWBase::Environment::get().getWorld()->teleportToClosestMarker(target, marker);
                anim->removeEffect(effectId);
                const ESM::Static* fx = MWBase::Environment::get().getWorld()->getStore().get<ESM::Static>()
                    .search("VFX_Summon_end");
                if (fx)
                    anim->addEffect("meshes\\\\" + fx->mModel, -1);
'''
new = '''                MWBase::Environment::get().getWorld()->teleportToClosestMarker(target, marker);
                // Teleport can rebuild the player's render object. Never keep an
                // Animation* acquired before the cell/world transition.
                anim = MWBase::Environment::get().getWorld()->getAnimation(target);
                if (anim)
                {
                    anim->removeEffect(effectId);
                    const ESM::Static* fx = MWBase::Environment::get().getWorld()->getStore().get<ESM::Static>()
                        .search("VFX_Summon_end");
                    if (fx)
                        anim->addEffect("meshes\\\\" + fx->mModel, -1);
                }
'''
text = replace_once(text, old, new, 'spellcasting.cpp intervention animation lifetime')
old = '''                    action.execute(target);
                    anim->removeEffect(effectId);
'''
new = '''                    action.execute(target);
                    // ActionTeleport may rebuild the target animation.
                    anim = MWBase::Environment::get().getWorld()->getAnimation(target);
                    if (anim)
                        anim->removeEffect(effectId);
'''
text = replace_once(text, old, new, 'spellcasting.cpp recall animation lifetime')
save(rel, text)


# 3) Attached spell effects: make scene-graph mutation null/exception safe.
rel = 'apps/openmw/mwrender/animation.cpp'
text = load(rel)
text = replace_once(
    text,
    '''    void Animation::addSpellCastGlow(const ESM::MagicEffect *effect, float glowDuration)\n    {\n        osg::Vec4f glowColor(1,1,1,1);\n''',
    '''    void Animation::addSpellCastGlow(const ESM::MagicEffect *effect, float glowDuration)\n    {\n        if (!effect || !mObjectRoot)\n            return;\n\n        osg::Vec4f glowColor(1,1,1,1);\n''',
    'animation.cpp spell glow root guard',
)
text = replace_once(
    text,
    '''    void Animation::addEffect (const std::string& model, int effectId, bool loop, const std::string& bonename, const std::string& texture)\n    {\n        if (!mObjectRoot.get())\n            return;\n''',
    '''    void Animation::addEffect (const std::string& model, int effectId, bool loop, const std::string& bonename, const std::string& texture)\n    {\n        // Effects are cosmetic. A transient/departed Ptr or scene root should\n        // never be allowed to take the whole Android client down.\n        if (!mObjectRoot || !mInsert || mPtr.isEmpty() || model.empty())\n            return;\n''',
    'animation.cpp addEffect root/ptr guard',
)
text = replace_once(
    text,
    '''            NodeMap::const_iterator found = getNodeMap().find(Misc::StringUtils::lowerCase(bonename));\n            if (found == getNodeMap().end())\n                throw std::runtime_error("Can't find bone " + bonename);\n\n            parentNode = found->second;\n''',
    '''            NodeMap::const_iterator found = getNodeMap().find(Misc::StringUtils::lowerCase(bonename));\n            if (found == getNodeMap().end() || !found->second)\n            {\n                Log(Debug::Warning) << "Skipping VFX '" << model << "': missing bone " << bonename;\n                return;\n            }\n\n            parentNode = found->second;\n''',
    'animation.cpp missing VFX bone',
)
text = replace_once(
    text,
    '''        parentNode->addChild(trans);\n        osg::ref_ptr<osg::Node> node = mResourceSystem->getSceneManager()->getInstance(model, trans);\n        node->getOrCreateStateSet()->setMode(GL_LIGHTING, osg::StateAttribute::OFF);\n''',
    '''        parentNode->addChild(trans);\n        osg::ref_ptr<osg::Node> node;\n        try\n        {\n            node = mResourceSystem->getSceneManager()->getInstance(model, trans);\n        }\n        catch (const std::exception& e)\n        {\n            parentNode->removeChild(trans);\n            Log(Debug::Warning) << "Skipping VFX '" << model << "': " << e.what();\n            return;\n        }\n        if (!node)\n        {\n            parentNode->removeChild(trans);\n            Log(Debug::Warning) << "Skipping VFX '" << model << "': scene instance is null";\n            return;\n        }\n        node->getOrCreateStateSet()->setMode(GL_LIGHTING, osg::StateAttribute::OFF);\n''',
    'animation.cpp VFX instance exception guard',
)
text = replace_once(
    text,
    '''    void Animation::removeEffect(int effectId)\n    {\n        RemoveCallbackVisitor visitor(effectId);\n''',
    '''    void Animation::removeEffect(int effectId)\n    {\n        if (!mInsert)\n        {\n            mHasMagicEffects = false;\n            return;\n        }\n        RemoveCallbackVisitor visitor(effectId);\n''',
    'animation.cpp removeEffect insert guard',
)
text = replace_once(
    text,
    '''    void Animation::getLoopingEffects(std::vector<int> &out) const\n    {\n        if (!mHasMagicEffects)\n            return;\n''',
    '''    void Animation::getLoopingEffects(std::vector<int> &out) const\n    {\n        if (!mHasMagicEffects || !mInsert)\n            return;\n''',
    'animation.cpp getLoopingEffects insert guard',
)
text = replace_once(
    text,
    '''    void Animation::updateEffects()\n    {\n        // We do not need to visit scene every frame.\n        // We can use a bool flag to check in spellcasting effect found.\n        if (!mHasMagicEffects)\n            return;\n''',
    '''    void Animation::updateEffects()\n    {\n        // We do not need to visit scene every frame.\n        // We can use a bool flag to check in spellcasting effect found.\n        if (!mHasMagicEffects || !mInsert)\n            return;\n''',
    'animation.cpp updateEffects insert guard',
)
save(rel, text)


# 4) Free/world VFX (including summon end): a missing or unsupported model is a
#    visual failure, not a fatal gameplay exception.
rel = 'apps/openmw/mwrender/effectmanager.cpp'
text = load(rel)
text = replace_once(
    text,
    '#include \"effectmanager.hpp\"\n\n',
    '#include \"effectmanager.hpp\"\n\n#include <exception>\n',
    'effectmanager.cpp exception include',
)
text = replace_once(
    text,
    '#include <components/resource/scenemanager.hpp>\n',
    '#include <components/resource/scenemanager.hpp>\n#include <components/debug/debuglog.hpp>\n',
    'effectmanager.cpp debug include',
)
old = '''void EffectManager::addEffect(const std::string &model, const std::string& textureOverride, const osg::Vec3f &worldPosition, float scale, bool isMagicVFX)\n{\n    osg::ref_ptr<osg::Node> node = mResourceSystem->getSceneManager()->getInstance(model);\n\n    node->setNodeMask(Mask_Effect);\n'''
new = '''void EffectManager::addEffect(const std::string &model, const std::string& textureOverride, const osg::Vec3f &worldPosition, float scale, bool isMagicVFX)\n{\n    if (model.empty() || !mParentNode || !mResourceSystem)\n        return;\n\n    osg::ref_ptr<osg::Node> node;\n    try\n    {\n        node = mResourceSystem->getSceneManager()->getInstance(model);\n    }\n    catch (const std::exception& e)\n    {\n        Log(Debug::Warning) << "Skipping free VFX '" << model << "': " << e.what();\n        return;\n    }\n    if (!node)\n    {\n        Log(Debug::Warning) << "Skipping free VFX '" << model << "': scene instance is null";\n        return;\n    }\n\n    node->setNodeMask(Mask_Effect);\n'''
text = replace_once(text, old, new, 'effectmanager.cpp safe addEffect')
save(rel, text)

print('ArenaMW Android magic/Mali stability patch applied')
