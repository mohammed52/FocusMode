package com.example.focusmode.ui.theme

import androidx.compose.foundation.shape.GenericShape

// A pointed/Fatimid-style arch silhouette — flat sides up to a "shoulder," then two curves
// meeting at a point at the top, rather than a smooth round dome. Used for the icon badges in
// onboarding/permissions/the widget tutorial in place of a plain circle; left out of avatar
// monograms and general component shapes (cards/buttons keep Material3's default rounding), since
// applying it everywhere would fight Material3's own component shapes rather than read as a
// deliberate accent.
val ArchShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val shoulderY = h * 0.42f
    val controlY = h * 0.12f
    moveTo(0f, h)
    lineTo(w, h)
    lineTo(w, shoulderY)
    quadraticBezierTo(w, controlY, w * 0.5f, 0f)
    quadraticBezierTo(0f, controlY, 0f, shoulderY)
    close()
}
