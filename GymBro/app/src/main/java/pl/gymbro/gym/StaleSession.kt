package pl.gymbro.gym

/**
 * Co zrobić z treningiem, którego nikt nie zakończył.
 *
 * Trening kończy się naciśnięciem „Zakończ" - a o tym łatwo zapomnieć, bo po
 * ostatniej serii człowiek odkłada telefon i wychodzi. Bez tej reguły sesja
 * zostaje otwarta w nieskończoność: aplikacja wita ekranem trwającego treningu,
 * licznik pokazuje dni zamiast minut, a lista planów jest nieosiągalna.
 *
 * Czas zakończenia bierzemy z ostatniej serii, nie z chwili, w której się
 * zorientowaliśmy. Trening skończył się wtedy, gdy przestałeś zapisywać -
 * doliczanie reszty doby zamieniłoby historię w fikcję.
 */
object StaleSession {

    /**
     * Po ilu godzinach bez zakończenia uznajemy trening za porzucony.
     *
     * Osiem, bo musi być dłużej niż najdłuższy prawdziwy trening i krócej niż
     * noc. Przy krótszym progu przerwa na telefon czy dojazd między seriami
     * mogłaby zamknąć trwającą sesję; przy dłuższym porzucona sesja przeżyłaby
     * do następnego dnia i znów zasłoniła ekran.
     */
    const val STALE_AFTER_MS: Long = 8 * 3_600_000L

    sealed interface Verdict {
        /** Trening trwa - nie ruszamy. */
        data object Active : Verdict

        /** Porzucony i pusty: nie zapisano ani jednej serii, nie ma czego archiwizować. */
        data object Discard : Verdict

        /** Porzucony, ale coś w nim jest - domykamy na ostatniej serii. */
        data class Close(val endedAt: Long) : Verdict
    }

    /**
     * @param startedAt początek sesji
     * @param lastSetAt czas ostatniej zapisanej serii albo null, gdy nie było żadnej
     * @param now chwila bieżąca
     * @param fromWatch czy sesję założył zegarek (ma znacznik `local_id`)
     */
    fun verdict(startedAt: Long, lastSetAt: Long?, now: Long, fromWatch: Boolean = false): Verdict {
        if (now - startedAt < STALE_AFTER_MS) return Verdict.Active
        // Pusta sesja z zegarka nie jest pusta, tylko jeszcze nie dojechala.
        // START doszedl, a serie czekaja w kolejce zegarka z numerem tej sesji -
        // wystarczy, ze usluga pilota padla zaraz po starcie. Skasowana sesja
        // oznaczala, ze kazda zalegla seria i FINISH dostawaly 404, zegarek
        // wyrzucal je jako odmowe i caly trening przepadal. Zamknieta na
        // godzinie startu czeka na nie, a FINISH poprawi godzine konca.
        if (lastSetAt == null) return if (fromWatch) Verdict.Close(startedAt) else Verdict.Discard
        // Seria zapisana przed startem sesji nie powinna się zdarzyć, ale gdyby
        // zegar systemowy przeskoczył, nie chcemy ujemnego czasu trwania.
        return Verdict.Close(maxOf(lastSetAt, startedAt))
    }
}
