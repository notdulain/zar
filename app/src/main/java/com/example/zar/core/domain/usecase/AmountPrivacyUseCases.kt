package com.example.zar.core.domain.usecase

import com.example.zar.core.domain.repository.AmountPrivacyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Whether amounts are hidden, now and as it changes. */
class ObserveAmountsHiddenUseCase @Inject constructor(
    private val privacy: AmountPrivacyRepository,
) {
    fun current(): Boolean = privacy.isHidden()

    operator fun invoke(): Flow<Boolean> = privacy.observeHidden()
}

/** Hides or shows amounts everywhere. Records are never changed, only how they are displayed. */
class SetAmountsHiddenUseCase @Inject constructor(
    private val privacy: AmountPrivacyRepository,
) {
    suspend operator fun invoke(hidden: Boolean): Result<Unit> = resultOf { privacy.setHidden(hidden) }
}
