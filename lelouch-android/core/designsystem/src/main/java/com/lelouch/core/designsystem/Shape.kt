package com.lelouch.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val LelouchShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), // Tarjetas de canales y películas estándar
    large = RoundedCornerShape(16.dp),  // Paneles y modales
    extraLarge = RoundedCornerShape(24.dp)
)

// Tokens específicos para UI de TV y móvil
val LelouchCardCornerRadius = 12.dp
val LelouchBadgeCornerRadius = 6.dp
val LelouchDialogCornerRadius = 20.dp
