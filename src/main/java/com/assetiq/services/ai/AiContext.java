package com.assetiq.services.ai;

import java.util.List;
import java.util.Set;

/**
 * The retrieval result for one question: the prompt block the model is shown,
 * the records behind it, and what the caller was not allowed to see.
 *
 * @param dataBlock  sanitised, fenced tenant data for the system prompt
 * @param sources    records included, for citation back to the caller
 * @param included   sections the caller could read
 * @param denied     sections withheld because the caller lacks the authority
 */
public record AiContext(String dataBlock,
                        List<AiSource> sources,
                        Set<AiDataSection> included,
                        Set<AiDataSection> denied) {
}
