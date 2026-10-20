package com.example.zar.features.freelance.domain

import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.Money
import java.time.LocalDate

enum class FreelanceProblem {
    NAME_MISSING,
    NAME_TOO_LONG,
    CLIENT_TOO_LONG,
    AMOUNT_MISSING,
    AMOUNT_INVALID,
    /** Linking more than the receipt still has unlinked. */
    MORE_THAN_RECEIPT,
    /** Linking more than the milestone still expects. */
    MORE_THAN_REMAINING,
}

data class ProjectInput(val name: String, val client: String?)

data class MilestoneInput(val title: String, val amount: Money, val dueDate: LocalDate)

sealed interface Checked<out T> {
    data class Valid<T>(val value: T) : Checked<T>

    data class Invalid(val problems: Set<FreelanceProblem>) : Checked<Nothing>
}

/** Pure validation for freelance forms. Limits match firestore.rules. */
object FreelanceRules {
    const val MAX_PROJECT_NAME = 80
    const val MAX_CLIENT = 80
    const val MAX_MILESTONE_TITLE = 120

    fun checkProject(name: String, client: String): Checked<ProjectInput> {
        val problems = mutableSetOf<FreelanceProblem>()
        val trimmedName = name.trim()
        val trimmedClient = client.trim()
        if (trimmedName.isEmpty()) problems += FreelanceProblem.NAME_MISSING
        if (trimmedName.length > MAX_PROJECT_NAME) problems += FreelanceProblem.NAME_TOO_LONG
        if (trimmedClient.length > MAX_CLIENT) problems += FreelanceProblem.CLIENT_TOO_LONG
        return if (problems.isEmpty()) {
            Checked.Valid(ProjectInput(trimmedName, trimmedClient.ifEmpty { null }))
        } else {
            Checked.Invalid(problems)
        }
    }

    fun checkMilestone(title: String, amount: String, currency: Currency, dueDate: LocalDate): Checked<MilestoneInput> {
        val problems = mutableSetOf<FreelanceProblem>()
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) problems += FreelanceProblem.NAME_MISSING
        if (trimmedTitle.length > MAX_MILESTONE_TITLE) problems += FreelanceProblem.NAME_TOO_LONG
        val money = parsePositive(amount, currency, problems)
        return if (problems.isEmpty() && money != null) {
            Checked.Valid(MilestoneInput(trimmedTitle, money, dueDate))
        } else {
            Checked.Invalid(problems)
        }
    }

    /** The amount of a receipt to link: within what the receipt has left and what the milestone still expects. */
    fun checkLink(amount: String, available: Money, remaining: Money): Checked<Money> {
        val problems = mutableSetOf<FreelanceProblem>()
        val money = parsePositive(amount, available.currency, problems)
        if (money != null) {
            if (money > available) problems += FreelanceProblem.MORE_THAN_RECEIPT
            if (money > remaining) problems += FreelanceProblem.MORE_THAN_REMAINING
        }
        return if (problems.isEmpty() && money != null) Checked.Valid(money) else Checked.Invalid(problems)
    }

    private fun parsePositive(text: String, currency: Currency, problems: MutableSet<FreelanceProblem>): Money? {
        if (text.isBlank()) {
            problems += FreelanceProblem.AMOUNT_MISSING
            return null
        }
        return Money.parse(text, currency)?.takeIf { it.isPositive }
            ?: null.also { problems += FreelanceProblem.AMOUNT_INVALID }
    }
}
