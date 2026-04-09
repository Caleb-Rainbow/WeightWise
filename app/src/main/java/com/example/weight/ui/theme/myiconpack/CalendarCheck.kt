package com.example.weight.ui.theme.myiconpack

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.example.weight.ui.theme.MyIconPack

public val MyIconPack.CalendarCheck: ImageVector
    get() {
        if (_calendarCheck != null) {
            return _calendarCheck!!
        }
        _calendarCheck = Builder(name = "CalendarCheck", defaultWidth = 24.0.dp, defaultHeight =
                24.0.dp, viewportWidth = 960.0f, viewportHeight = 960.0f).apply {
            path(fill = SolidColor(Color(0xFF1f1f1f)), stroke = null, strokeLineWidth = 0.0f,
                    strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                    pathFillType = NonZero) {
                moveTo(200.0f, 880.0f)
                quadToRelative(-33.0f, 0.0f, -56.5f, -23.5f)
                reflectiveQuadTo(120.0f, 800.0f)
                verticalLineToRelative(-560.0f)
                quadToRelative(0.0f, -33.0f, 23.5f, -56.5f)
                reflectiveQuadTo(200.0f, 160.0f)
                horizontalLineToRelative(40.0f)
                verticalLineToRelative(-80.0f)
                horizontalLineToRelative(80.0f)
                verticalLineToRelative(80.0f)
                horizontalLineToRelative(320.0f)
                verticalLineToRelative(-80.0f)
                horizontalLineToRelative(80.0f)
                verticalLineToRelative(80.0f)
                horizontalLineToRelative(40.0f)
                quadToRelative(33.0f, 0.0f, 56.5f, 23.5f)
                reflectiveQuadTo(840.0f, 240.0f)
                verticalLineToRelative(255.0f)
                lineToRelative(-80.0f, 80.0f)
                verticalLineToRelative(-175.0f)
                lineTo(200.0f, 400.0f)
                verticalLineToRelative(400.0f)
                horizontalLineToRelative(248.0f)
                lineToRelative(80.0f, 80.0f)
                lineTo(200.0f, 880.0f)
                close()
                moveTo(200.0f, 320.0f)
                horizontalLineToRelative(560.0f)
                verticalLineToRelative(-80.0f)
                lineTo(200.0f, 240.0f)
                verticalLineToRelative(80.0f)
                close()
                moveTo(200.0f, 320.0f)
                verticalLineToRelative(-80.0f)
                verticalLineToRelative(80.0f)
                close()
                moveTo(662.0f, 900.0f)
                lineTo(520.0f, 758.0f)
                lineToRelative(56.0f, -56.0f)
                lineToRelative(85.0f, 85.0f)
                lineToRelative(170.0f, -170.0f)
                lineToRelative(56.0f, 57.0f)
                lineTo(662.0f, 900.0f)
                close()
            }
        }
        .build()
        return _calendarCheck!!
    }

private var _calendarCheck: ImageVector? = null
