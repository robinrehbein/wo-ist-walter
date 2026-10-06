package de.wuselburg.render

import android.graphics.Canvas
import de.wuselburg.core.Drawable

/* Port of personSvg() from scene.js (all body / bottom / hair / hat / pattern / bag / prop / pose variants). */

internal fun Fig.person(canvas: Canvas, d: Drawable) = figure(canvas) {
    val body = figStr(d, "body", "adult")
    val bottom = figStr(d, "bottom", "pants")
    val pose = figStr(d, "pose", "stand")
    val seat = figStr(d, "seat", "ground")
    val bag = figStr(d, "bag", "none")
    val prop = figStr(d, "prop", "none")
    val pattern = figStr(d, "pattern", "none")
    val hairStyle = figStr(d, "hairStyle", "short")
    val hat = figStr(d, "hat", "none")
    val shirt = c(figStr(d, "shirt", "#4a7fd6"))
    val skin = c(figStr(d, "skin", "#f6d2b0"))
    val hair = c(figStr(d, "hair", "#2d2118"))
    val pants = c(figStr(d, "pants", "#3b4a6b"))
    val shoes = c(figStr(d, "shoes", "#3a2b22"))
    val hatColor = c(figStr(d, "hatColor", "#e05a5a"))
    val patternColor = c(figStr(d, "patternColor", "#ffffff"))
    val propColor = c(figStr(d, "propColor", "#e05a5a"))

    val k = if (body == "kid") 0.74f else if (body == "senior") 0.95f else 1f
    val sit = pose == "sit"
    val seatLift = if (seat == "bench") 9f else 0f
    val oy = if (sit) 10f - seatLift else 0f
    val headR = if (body == "kid") 8.4f else 7.5f
    val legCol = if (bottom == "pants") pants else skin
    val white = c("#ffffff")
    val ink = c("#222222")

    shadow(11f, 3f, Fig.SHADOW)
    cv.save()
    cv.scale(k, k)
    outline = true

    if (bag == "backpack") {
        cv.save(); cv.translate(0f, oy)
        rect(3f, -34f, 9f, 16f, 3f, propColor)
        cv.restore()
    }
    if (sit) {
        // legs stretched forward
        cv.save(); cv.translate(0f, -seatLift)
        rect(-4f, -7f, 18f, 5.6f, 2.6f, legCol)
        ell(14.5f, -4.2f, 2.6f, 3.2f, shoes)
        cv.restore()
    } else if (bottom == "pants") {
        rect(-6.5f, -12f, 5.5f, 12f, 2f, pants); rect(1f, -12f, 5.5f, 12f, 2f, pants)
        ell(-3.7f, -1f, 3.4f, 2f, shoes); ell(3.7f, -1f, 3.4f, 2f, shoes)
    } else {
        rect(-5f, -12f, 3f, 12f, 1.4f, skin); rect(2f, -12f, 3f, 12f, 1.4f, skin)
        ell(-3.5f, -1f, 3.2f, 2f, shoes); ell(3.5f, -1f, 3.2f, 2f, shoes)
    }

    cv.save(); cv.translate(0f, oy)
    if (bottom == "skirt" && !sit) { poly(-8f, -22f, 8f, -22f, 11f, -9f, -11f, -9f); pathFill(pants) }
    rect(-11f, -34f, 4.2f, 17f, 2f, shirt); rect(6.8f, -34f, 4.2f, 17f, 2f, shirt)
    if (bottom == "dress") {
        path.rewind()
        path.moveTo(-8f, -34f); path.quadTo(-8f, -36f, -6f, -36f); path.lineTo(6f, -36f)
        path.quadTo(8f, -36f, 8f, -34f); path.lineTo(11f, -9f); path.lineTo(-11f, -9f); path.close()
        pathFill(shirt)
    } else {
        rect(-8f, -36f, 16f, 26f, 6f, shirt)
    }
    if (bottom != "dress") {
        if (pattern == "stripes") {
            rect(-7.6f, -31f, 15.2f, 2.4f, 0f, patternColor, false)
            rect(-7.6f, -25f, 15.2f, 2.4f, 0f, patternColor, false)
            rect(-7.6f, -19f, 15.2f, 2.4f, 0f, patternColor, false)
        }
        if (pattern == "dots") {
            circ(-4f, -30f, 1.5f, patternColor, false); circ(3f, -27f, 1.5f, patternColor, false)
            circ(-3f, -21f, 1.5f, patternColor, false); circ(4f, -17f, 1.5f, patternColor, false)
            circ(0f, -14f, 1.5f, patternColor, false)
        }
        if (pattern == "jacket") {
            path.rewind(); path.moveTo(0f, -36f); path.lineTo(0f, -10f); pathStroke(patternColor, 1.4f)
            poly(-4.5f, -36f, 0f, -29f, 4.5f, -36f); pathFill(white, false)
        }
    } else if (pattern == "dots") {
        circ(-3f, -28f, 1.5f, patternColor, false); circ(3f, -22f, 1.5f, patternColor, false)
        circ(-4f, -15f, 1.5f, patternColor, false)
    }
    if (bag == "tote") {
        path.rewind(); path.moveTo(-7f, -29f); path.quadTo(-13f, -21f, -10f, -18f); pathStroke(c("#777777"), .9f)
        rect(-15f, -19f, 9f, 8f, 1.5f, propColor)
    }

    // head
    circ(0f, -43.5f, headR, skin)
    val arcOk = hairStyle == "short" || hairStyle == "long" || hairStyle == "bun" || hairStyle == "pony"
    if (arcOk) {
        path.rewind(); path.moveTo(-headR, -43.5f)
        domeTo(-headR, -43.5f - headR, headR, -43.5f + headR)
        path.quadTo(0f, -47f, -headR, -43.5f); path.close()
        pathFill(hair)
    }
    when (hairStyle) {
        "long" -> {
            rect(-headR - 1f, -44f, 3.4f, 14f, 1.7f, hair)
            rect(headR - 2.4f, -44f, 3.4f, 14f, 1.7f, hair)
        }
        "bun" -> circ(0f, -52.5f, 3.6f, hair)
        "curly" -> {
            circ(-6f, -47f, 3.6f, hair); circ(-2.5f, -50.5f, 3.6f, hair); circ(2.5f, -50.5f, 3.6f, hair)
            circ(6f, -47f, 3.6f, hair); circ(0f, -47.5f, 4f, hair)
        }
        "pony" -> ell(9f, -41f, 2.6f, 6f, hair, -15f)
    }
    circ(-2.6f, -42.5f, 1f, ink, false); circ(2.6f, -42.5f, 1f, ink, false)
    circ(-5f, -40f, 1.4f, Fig.CHEEK_PERSON, false); circ(5f, -40f, 1.4f, Fig.CHEEK_PERSON, false)
    path.rewind(); path.moveTo(-2.2f, -39.8f); path.quadTo(0f, -38f, 2.2f, -39.8f)
    pathStroke(c("#7a3b2a"), .7f, true)
    if (figBool(d, "beard", false)) {
        path.rewind(); path.moveTo(-6.4f, -41f); path.quadTo(0f, -30f, 6.4f, -41f); path.quadTo(0f, -36f, -6.4f, -41f)
        path.close(); pathFill(hair, false)
    }
    if (figBool(d, "glasses", false)) {
        val g = c("#333333")
        circS(-2.9f, -42.5f, 2.3f, g, .8f); circS(2.9f, -42.5f, 2.3f, g, .8f)
        lineS(-0.6f, -42.5f, 0.6f, -42.5f, g, .8f)
    }
    when (hat) {
        "cap" -> {
            path.rewind(); path.moveTo(-8f, -45.5f); domeTo(-8f, -53.5f, 8f, -37.5f); path.close(); pathFill(hatColor)
            rect(2f, -47f, 9.5f, 2.6f, 1.2f, hatColor)
        }
        "beanie" -> {
            path.rewind(); path.moveTo(-8f, -45f); domeTo(-8f, -54.5f, 8f, -35.5f); path.close(); pathFill(hatColor)
            circ(0f, -54f, 2.2f, white)
        }
        "top" -> {
            rect(-9.5f, -50f, 19f, 3f, 1.5f, ink)
            rect(-6f, -60f, 12f, 11f, 1.5f, ink)
            rect(-6f, -53f, 12f, 2.6f, 0f, hatColor, false)
        }
        "sun" -> {
            ell(0f, -47.5f, 13f, 3.2f, hatColor)
            path.rewind(); path.moveTo(-7f, -48f); domeTo(-7f, -55f, 7f, -41f); path.close(); pathFill(hatColor)
        }
        "band" -> rect(-7.6f, -48.4f, 15.2f, 2.6f, 1.3f, hatColor, false)
    }

    // props
    when (prop) {
        "balloon" -> {
            path.rewind(); path.moveTo(11f, -27f); path.quadTo(18f, -50f, 14f, -72f); pathStroke(c("#777777"), .8f)
            ell(14f, -80f, 7f, 9f, propColor)
        }
        "icecream" -> {
            poly(10f, -26f, 13f, -18f, 16f, -26f); pathFill(c("#e8b46a"))
            circ(13f, -29f, 3.6f, c("#f7c6d9")); circ(13f, -33.2f, 3f, c("#fff4e0"))
        }
        "umbrella" -> {
            lineS(12f, -34f, 12f, -62f, c("#555555"), 1f)
            path.rewind(); path.moveTo(-6f, -62f); path.quadTo(12f, -84f, 30f, -62f)
            path.quadTo(21f, -66f, 12f, -62f); path.quadTo(3f, -66f, -6f, -62f); path.close()
            pathFill(propColor)
        }
        "guitar" -> {
            cv.save(); cv.translate(9f, -22f); cv.rotate(-30f)
            ell(0f, 0f, 7f, 5.5f, c("#b9792f"))
            circ(-1f, 0f, 2f, c("#4a2f17"), false)
            rect(5f, -1.2f, 14f, 2.4f, 0f, c("#6b4423"))
            cv.restore()
        }
        "camera" -> {
            rect(6f, -41f, 9f, 6.5f, 1.4f, c("#444444"))
            circ(10.5f, -37.8f, 2.2f, c("#99aadd"))
        }
    }
    if (body == "senior" && prop == "none") {
        path.rewind(); path.moveTo(12f, -26f); path.lineTo(12f, -3f); path.rQuadTo(0f, -3f, -3f, -3f)
        pathStroke(c("#6b4a2b"), 1.6f, true)
    }
    cv.restore() // translate(oy)
    cv.restore() // scale(k)
}
