package com.example.zar.features.quicknote.domain.usecase

import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.features.quicknote.domain.model.QuickNoteAccountContext
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError.InputProblem
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftRequest
import com.example.zar.features.quicknote.domain.model.QuickNoteLimits
import com.example.zar.features.quicknote.domain.repository.QuickNoteDraftRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Interprets a typed note as a pending draft. The caller keeps [note] for manual entry if this fails, and must
 * clear it on discard, completion and sign-out. The result holds structured proposals only, never the note.
 */
class GenerateQuickNoteDraftUseCase internal constructor(
    private val drafts: QuickNoteDraftRepository,
    private val accounts: AccountRepository,
    private val today: () -> LocalDate,
) {
    @Inject
    constructor(drafts: QuickNoteDraftRepository, accounts: AccountRepository) : this(drafts, accounts, ZarTime::today)

    suspend operator fun invoke(note: String): Result<Draft> = try {
        Result.success(generate(note))
    } catch (e: CancellationException) {
        throw e
    } catch (e: QuickNoteDraftError) {
        Result.failure(e)
    } catch (_: Exception) {
        // Anything unexpected is reported as a generic failure; its message could echo user text.
        Result.failure(QuickNoteDraftError.ServiceUnavailable())
    }

    private suspend fun generate(note: String): Draft {
        val text = note.trim()
        if (text.isEmpty()) throw QuickNoteDraftError.InvalidInput(InputProblem.BLANK)
        if (text.length > QuickNoteLimits.MAX_NOTE_LENGTH) throw QuickNoteDraftError.InvalidInput(InputProblem.TOO_LONG)

        val context = accounts.observeAccounts().first()
            .filterNot { it.isArchived }
            .sortedBy { it.name.lowercase() }
            .take(QuickNoteLimits.MAX_ACCOUNTS)
            .map {
                QuickNoteAccountContext(
                    id = it.id,
                    name = it.name.trim().take(QuickNoteLimits.MAX_ACCOUNT_NAME_LENGTH),
                    type = it.type,
                    currency = it.currency,
                )
            }
        return drafts.generate(QuickNoteDraftRequest(note = text, today = today(), accounts = context))
    }
}
