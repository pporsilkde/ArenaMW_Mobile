/*
    Copyright (C) 2018, 2019 Ilya Zhuravlev

    This file is part of OpenMW-Android.

    OpenMW-Android is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    OpenMW-Android is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with OpenMW-Android.  If not, see <https://www.gnu.org/licenses/>.
*/

package ui.controls

import android.content.Context
import android.util.AttributeSet

import org.libsdl.app.SDLActivity

class JoystickRight : Joystick {

    private var curX = 0f
    private var curY = 0f

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int)
        : super(context, attrs, defStyle)

    override fun onTrackedPointerDown(x: Float, y: Float) {
        super.onTrackedPointerDown(x, y)
        // Rebase relative mouse movement whenever Android hands this view a new
        // pointer.  This prevents a camera jump during pointer recovery/handoff.
        curX = x
        curY = y
    }

    override fun onTrackedPointerMove(x: Float, y: Float) {
        // this isn't configurable here but configurable in openmw built-in settings
        val mouseScalingFactor = 900f

        if (width > 0 && height > 0) {
            val movementX = (x - curX) * mouseScalingFactor / width
            val movementY = (y - curY) * mouseScalingFactor / height

            SDLActivity.sendRelativeMouseMotion(Math.round(movementX), Math.round(movementY))
        }

        curX = x
        curY = y
        super.onTrackedPointerMove(x, y)
    }
}
