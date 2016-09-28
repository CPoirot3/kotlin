fun box(): String {
    // D = 1101 C = 1100
    // 6 = 0110 5 = 0101
    val iarg1: Int = 0xDC56DC56.toInt()
    val iarg2: Int = 0x65DC65DC
    val i1: Any = iarg1 and iarg2
    val i2: Any = iarg1 or  iarg2
    val i3: Any = iarg1 xor iarg2
    val i4: Any = iarg1.inv()
    val i5: Any = iarg1 shl 16
    val i6: Any = iarg1 shr 16
    val i7: Any = iarg1 ushr 16

    if (i1 != 0x44544454.toInt()) return "fail: Int.and"
    if (i2 != 0xFDDEFDDE.toInt()) return "fail: Int.or"
    if (i3 != 0xB98AB98A.toInt()) return "fail: Int.xor"
    if (i4 != 0x23A923A9.toInt()) return "fail: Int.inv"
    if (i5 != 0xDC560000.toInt()) return "fail: Int.shl"
    if (i6 != 0xFFFFDC56.toInt()) return "fail: Int.shr"
    if (i7 != 0x0000DC56.toInt()) return "fail: Int.ushr"


    // TODO: Use long hex constants after KT-4749 is fixed
    val larg1: Long = (0xDC56DC56L shl 32) + 0xDC56DC56 // !!!!
    val larg2: Long = 0x65DC65DC65DC65DC
    val l1: Any = larg1 and larg2
    val l2: Any = larg1 or  larg2
    val l3: Any = larg1 xor larg2
    val l4: Any = larg1.inv()
    val l5: Any = larg1 shl 32
    val l6: Any = larg1 shr 32
    val l7: Any = larg1 ushr 32
    
    if (l1 != 0x4454445444544454) return "fail: Long.and"
    if (l2 != (0xFDDEFDDEL shl 32) + 0xFDDEFDDE) return "fail: Long.or"
    if (l3 != (0xB98AB98AL shl 32) + 0xB98AB98A) return "fail: Long.xor"
    if (l4 != 0x23A923A923A923A9) return "fail: Long.inv"
    if (l5 != (0xDC56DC56L shl 32)/*!!!*/) return "fail: Long.shl"
    if (l6 != (0xFFFFFFFFL shl 32) + 0xDC56DC56) return "fail: Long.shr"
    if (l7 != (0x00000000L shl 32) + 0xDC56DC56.toLong()) return "fail: Long.ushr"

    val sarg1: Short = 0xDC56.toShort()
    val sarg2: Short = 0x65DC.toShort()
    val s1: Any = sarg1 and sarg2
    val s2: Any = sarg1 or  sarg2
    val s3: Any = sarg1 xor sarg2
    val s4: Any = sarg1.inv()
    val s5: Any = sarg1 shl 8
    val s6: Any = sarg1 shr 8
    val s7: Any = sarg1 ushr 8
    
    if (s1 != 0x4454.toShort()) return "fail: Short.and"
    if (s2 != 0xFDDE.toShort()) return "fail: Short.or"
    if (s3 != 0xB98A.toShort()) return "fail: Short.xor"
    if (s4 != 0x23A9.toShort()) return "fail: Short.inv"
    if (s5 != 0x5600.toShort()) return "fail: Short.shl"
    if (s6 != 0xFFDC.toShort()) return "fail: Short.shr"
    // TODO: if (s7 != 0x00DC.toShort()) return "fail: Short.ushr"

    val barg1: Byte = 0xDC.toByte()
    val barg2: Byte = 0x65.toByte()
    val b1: Any = barg1 and barg2
    val b2: Any = barg1 or  barg2
    val b3: Any = barg1 xor barg2
    val b4: Any = barg1.inv()
    val b5: Any = barg1 shl 4
    val b6: Any = barg1 shr 4
    val b7: Any = barg1 ushr 4
    
    if (b1 != 0x44.toByte()) return "fail: Byte.and"
    if (b2 != 0xFD.toByte()) return "fail: Byte.or"
    if (b3 != 0xB9.toByte()) return "fail: Byte.xor"
    if (b4 != 0x23.toByte()) return "fail: Byte.inv"
    if (b5 != 0xC0.toByte()) return "fail: Byte.shl"
    if (b6 != 0xFD.toByte()) return "fail: Byte.shr"
    // TODO: if (b7 != 0x0D.toByte()) return "fail: Byte.ushr"

    return "OK"
}