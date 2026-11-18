package cz.titulky.tv

/** Souřadnice z OCR bez závislosti na Androidu; blok je identita pouze uvnitř jednoho snímku. */
internal data class OcrKus(
    val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int,
    val blok: Int = -1
) {
    val stred get() = (top + bottom) / 2
}

internal data class OcrRadek(
    val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int,
    val kusy: List<OcrKus>
)

/** Geometrická část původní čtečky, kterou lze přehrávat nad výsledky OCR bez zařízení. */
internal object VyberTitulku {
    val JMENO = Regex("^[A-Z][A-Za-z0-9 .'-]{1,24}:\\s")

    fun pocetSlov(text: String) = text.split(Regex("\\s+")).count { it.count(Char::isLetter) >= 2 }

    fun radky(kusy: List<OcrKus>, sirka: Int, upravJmeno: (String) -> String): List<OcrRadek> {
        val radky = mutableListOf<MutableList<OcrKus>>()
        for (k in kusy.filter { it.text.count(Char::isLetter) >= 2 }.sortedBy { it.top }) {
            val posledni = radky.lastOrNull()
            if (posledni != null && k.stred in (posledni.minOf { it.top }..posledni.maxOf { it.bottom })) posledni += k
            else radky += mutableListOf(k)
        }
        val skupiny = radky.flatMap { r ->
            val serazene = r.sortedBy { it.left }
            val vyska = (r.maxOf { it.bottom } - r.minOf { it.top }).coerceAtLeast(1)
            val vysledek = mutableListOf(mutableListOf(serazene.first()))
            for (k in serazene.drop(1)) {
                if (k.left - vysledek.last().last().right > vyska * 2.5f) vysledek += mutableListOf(k)
                else vysledek.last() += k
            }
            vysledek
        }
        return skupiny.map { r ->
            OcrRadek(upravJmeno(r.joinToString(" ") { it.text }),
                r.minOf { it.left }, r.minOf { it.top }, r.maxOf { it.right }, r.maxOf { it.bottom }, r)
        }.filter { it.text.count(Char::isLetter) >= 3 }
            .filter { (it.left + it.right) / 2f / sirka in 0.3f..0.7f }
            .filterNot { jeMenu(it.text) }
    }

    private fun jeMenu(text: String): Boolean {
        val pismena = text.filter(Char::isLetter)
        if (pismena.isEmpty()) return true
        val velka = pismena.count(Char::isUpperCase).toFloat() / pismena.length
        val interpunkce = text.any { it in ".,!?:;'" }
        if (velka > 0.8f && pocetSlov(text) <= 3 && !interpunkce) return true
        return velka > 0.85f && pocetSlov(text) >= 4
    }

    fun vyber(radky: List<OcrRadek>): List<OcrRadek> {
        if (radky.isEmpty()) return emptyList()
        val vybrane = ArrayDeque<OcrRadek>()
        var spodni = radky.last()
        vybrane.addFirst(spodni)
        if (!JMENO.containsMatchIn(spodni.text)) {
            for (r in radky.dropLast(1).asReversed()) {
                if (!navazuje(r, spodni)) break
                vybrane.addFirst(r)
                spodni = r
                if (JMENO.containsMatchIn(r.text)) break
            }
        }
        if (vybrane.size == 1 && JMENO.containsMatchIn(vybrane.first().text)) {
            var horni = vybrane.first()
            for (r in radky.dropLast(1).asReversed()) {
                if (vybrane.size >= 3) break
                val vyska = (horni.bottom - horni.top).coerceAtLeast(1)
                if (horni.top - r.bottom > vyska * 0.9) break
                if (!JMENO.containsMatchIn(r.text)) break
                vybrane.addFirst(r)
                horni = r
            }
        }
        return vybrane.toList()
    }

    private fun navazuje(horni: OcrRadek, spodni: OcrRadek): Boolean {
        val vyska = (spodni.bottom - spodni.top).coerceAtLeast(1)
        val mezera = spodni.top - horni.bottom
        if (mezera <= vyska * 0.9) return true
        // OCR blok dovolí větší řádkování, ale nespojí vzdálené odstavce ani dva sloupce.
        val bloky = horni.kusy.map { it.blok }.filter { it >= 0 }.toSet()
        val stejnyBlok = spodni.kusy.any { it.blok in bloky }
        val prekryv = minOf(horni.right, spodni.right) > maxOf(horni.left, spodni.left)
        return stejnyBlok && prekryv && mezera <= vyska * 2
    }

    data class Replika(val text: String, val prvniRadek: OcrRadek)

    fun repliky(radky: List<OcrRadek>): List<Replika> {
        val vysledek = mutableListOf<Replika>()
        for (r in radky) {
            if (vysledek.isEmpty() || JMENO.containsMatchIn(r.text)) vysledek += Replika(r.text, r)
            else vysledek[vysledek.lastIndex] = vysledek.last().copy(text = vysledek.last().text + " " + r.text)
        }
        return vysledek
    }
}
