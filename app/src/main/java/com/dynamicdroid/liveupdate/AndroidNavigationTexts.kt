package com.dynamicdroid.liveupdate

import android.content.Context
import com.dynamicdroid.R
import com.dynamicdroid.model.ManeuverType

class AndroidNavigationTexts(private val context: Context) : NavigationTexts {
    override fun instruction(type: ManeuverType, street: String): String {
        val res = when (type) {
            ManeuverType.DEPART -> R.string.maneuver_depart
            ManeuverType.STRAIGHT -> R.string.maneuver_straight
            ManeuverType.SLIGHT_LEFT -> R.string.maneuver_slight_left
            ManeuverType.LEFT -> R.string.maneuver_left
            ManeuverType.SHARP_LEFT -> R.string.maneuver_sharp_left
            ManeuverType.SLIGHT_RIGHT -> R.string.maneuver_slight_right
            ManeuverType.RIGHT -> R.string.maneuver_right
            ManeuverType.SHARP_RIGHT -> R.string.maneuver_sharp_right
            ManeuverType.U_TURN -> R.string.maneuver_u_turn
            ManeuverType.ROUNDABOUT -> R.string.maneuver_roundabout
            ManeuverType.ARRIVE -> R.string.maneuver_arrive
        }
        return context.getString(res, street)
    }

    override fun arrived(destination: String) = context.getString(R.string.arrived_title)
    override fun arrivedShort() = context.getString(R.string.arrived_short)
    override fun offRoute() = context.getString(R.string.off_route_title)
    override fun offRouteShort() = context.getString(R.string.off_route_short)
    override fun summary(duration: String, distance: String, eta: String) =
        context.getString(R.string.summary_format, duration, distance, eta)
}
