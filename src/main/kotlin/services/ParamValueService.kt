package services

import db.ParamValueRepository
import dto.ParamValueUsage

/** Maintenance over a low-cardinality param's value dictionary (V43). Ownership is checked by the
 *  caller (the MCP tool resolves the param through the user's own habit before delegating here). */
object ParamValueService {

    fun listValues(paramId: Long): List<ParamValueUsage> =
        ParamValueRepository.listValues(paramId)

    /** Merges [from] values into [into]; returns the number of check-ins repointed. */
    fun mergeValues(paramId: Long, from: List<String>, into: String): Int =
        ParamValueRepository.mergeValues(paramId, from, into)
}
