package db

import services.DatabaseService
import dto.ParamValueUsage
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using

/** The per-param dictionary of recurring values (`param_values`, V43; interning made automatic in V44). */
object ParamValueRepository {

    /** Distinct dictionary values of [paramId] with how many check-ins use each, most-used first. */
    fun listValues(paramId: Long): List<ParamValueUsage> =
        using(sessionOf(DatabaseService.dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    SELECT pv.value, count(v.value_id)::int AS uses
                    FROM param_values pv
                    LEFT JOIN checkin_values v ON v.value_id = pv.id
                    WHERE pv.param_id = ?
                    GROUP BY pv.value
                    ORDER BY uses DESC, pv.value
                    """.trimIndent(),
                    paramId
                ).map { ParamValueUsage(it.string("value"), it.int("uses")) }.asList
            )
        }

    /**
     * Merges every [from] value of [paramId] into [into]: repoints the check-ins that used them to
     * the (interned) target value, then drops the now-orphan dictionary rows. Repoint-before-delete
     * in one transaction so the `value_id` FK (ON DELETE RESTRICT, V43) is never violated.
     * Returns the number of check-in rows repointed. [into] is created if it doesn't exist yet.
     */
    fun mergeValues(paramId: Long, from: List<String>, into: String): Int {
        val sources = from.filterNot { it == into }.distinct()
        if (sources.isEmpty()) return 0
        val inClause = sources.joinToString(", ") { "?" }
        return using(sessionOf(DatabaseService.dataSource)) { session ->
            session.transaction { tx ->
                val targetId = tx.run(
                    queryOf("SELECT intern_param_value(?, ?) AS id", paramId, into)
                        .map { it.long("id") }.asSingle
                ) ?: error("intern_param_value returned no id")

                val repointed = tx.update(
                    queryOf(
                        """
                        UPDATE checkin_values
                        SET value_id = ?
                        WHERE value_id IN (
                            SELECT id FROM param_values WHERE param_id = ? AND value IN ($inClause)
                        )
                        """.trimIndent(),
                        *(listOf<Any?>(targetId, paramId) + sources).toTypedArray()
                    )
                )

                tx.update(
                    queryOf(
                        "DELETE FROM param_values WHERE param_id = ? AND value IN ($inClause)",
                        *(listOf<Any?>(paramId) + sources).toTypedArray()
                    )
                )
                repointed
            }
        }
    }
}
