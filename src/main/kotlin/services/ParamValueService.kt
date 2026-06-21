package services

import db.ParamValueRepository
import dto.ParamValueUsage

/** Maintenance over a param's recurring-value dictionary (V43/V44). Ownership is checked by the
 *  caller (the MCP tool resolves the param through the user's own track before delegating here). */
object ParamValueService {

    fun listValues(paramId: Long): List<ParamValueUsage> =
        ParamValueRepository.listValues(paramId)

    /** Merges [from] values into [into]; returns the number of check-ins repointed. */
    fun mergeValues(paramId: Long, from: List<String>, into: String): Int =
        ParamValueRepository.mergeValues(paramId, from, into)
}
