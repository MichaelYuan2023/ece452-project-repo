package com.example.houseflow.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Neutrals ────────────────────────────────────────────────────────────────
val Canvas = Color(0xFFFFFFFF)
val AppBackground = Color(0xFFF8F8FC)  // faint lavender tint — warm but not gray
val Ink = Color(0xFF1A1A2E)            // dark navy-black for headings
val Body = Color(0xFF44475A)           // softened body text
val Muted = Color(0xFF71748A)          // captions, secondary text
val Hairline = Color(0xFFE8E8F0)       // slightly blue-tinted border
val HairlineSoft = Color(0xFFF2F2F8)   // very subtle separator
val SurfaceCard = Color(0xFFF4F4FA)    // raised surface variant
val SurfaceSoft = Color(0xFFF9F9FD)    // mid-ground containers

// ─── Primary: Indigo ─────────────────────────────────────────────────────────
val BrandIndigo = Color(0xFF5B5BD6)
val BrandIndigoSoft = Color(0xFFF0F0FF)

// ─── Secondary: Teal ─────────────────────────────────────────────────────────
val Teal = Color(0xFF2DB8A1)
val TealSoft = Color(0xFFE8FAF6)

// ─── Tertiary: Mauve ─────────────────────────────────────────────────────────
val Mauve = Color(0xFF8E6EC2)
val MauveSoft = Color(0xFFF5F0FF)

// ─── Error: Coral ────────────────────────────────────────────────────────────
val Coral = Color(0xFFE5574F)
val CoralSoft = Color(0xFFFEF0EF)

// ─── Semantic accents ────────────────────────────────────────────────────────
val Success = Color(0xFF2DB8A1)        // alias of Teal
val Warning = Color(0xFFE5930B)
val Orange = Color(0xFFF97B3C)
val Pink = Color(0xFFD94E8F)

// ─── Gamification: Amber/Gold ────────────────────────────────────────────────
val Gold = Color(0xFFE5930B)
val GoldSoft = Color(0xFFFEF6E0)

// ─── Legacy aliases (keep for any file that still references old names) ──────
val BrandBlue = BrandIndigo
val BrandBlueSoft = BrandIndigoSoft
val Emerald = Teal
val EmeraldSoft = TealSoft
val Violet = Mauve
val VioletSoft = MauveSoft
val ErrorRed = Coral
val ErrorRedSoft = CoralSoft
