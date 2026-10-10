package com.dynamicdroid.liveupdate

import androidx.annotation.DrawableRes
import com.dynamicdroid.R
import com.dynamicdroid.model.ManeuverType

object ManeuverIcons {
    @DrawableRes
    fun forType(type: ManeuverType): Int = when (type) {
        ManeuverType.DEPART, ManeuverType.STRAIGHT -> R.drawable.ic_maneuver_straight
        ManeuverType.SLIGHT_LEFT -> R.drawable.ic_maneuver_slight_left
        ManeuverType.LEFT -> R.drawable.ic_maneuver_left
        ManeuverType.SHARP_LEFT -> R.drawable.ic_maneuver_sharp_left
        ManeuverType.SLIGHT_RIGHT -> R.drawable.ic_maneuver_slight_right
        ManeuverType.RIGHT -> R.drawable.ic_maneuver_right
        ManeuverType.SHARP_RIGHT -> R.drawable.ic_maneuver_sharp_right
        ManeuverType.U_TURN -> R.drawable.ic_maneuver_u_turn
        ManeuverType.ROUNDABOUT -> R.drawable.ic_maneuver_roundabout
        ManeuverType.ARRIVE -> R.drawable.ic_maneuver_arrive
    }
}
