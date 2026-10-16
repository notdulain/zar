package com.example.zar.features.freelance

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedPaymentId
import com.example.zar.core.domain.model.ExpectedPaymentStatus
import com.example.zar.core.domain.model.ExpectedSource
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.repository.ExpectedPaymentRepository
import com.example.zar.features.freelance.domain.model.Project
import com.example.zar.features.freelance.domain.model.ProjectId
import com.example.zar.features.freelance.domain.model.ProjectStatus
import com.example.zar.features.freelance.domain.repository.ProjectRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

object FreelanceFixtures {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 15)

    fun project(id: String, currency: Currency = Currencies.USD, status: ProjectStatus = ProjectStatus.ACTIVE, createdAtOffset: Long = 0) =
        Project(ProjectId(id), "Project $id", "Client $id", currency, status, T0.plusSeconds(createdAtOffset))

    fun milestone(
        id: String,
        projectId: String,
        amount: Money,
        due: LocalDate = TODAY.plusDays(10),
        status: ExpectedPaymentStatus = ExpectedPaymentStatus.OPEN,
    ) = ExpectedPayment(
        id = ExpectedPaymentId(id),
        direction = Direction.IN,
        amount = amount,
        dueDate = due,
        title = "Milestone $id",
        source = ExpectedSource.FreelanceMilestone(projectId, id),
        status = status,
        createdAt = T0,
    )

    fun match(milestoneId: String, txId: String, amount: Money, offset: Long = 0) =
        PaymentMatch(ExpectedPaymentId(milestoneId), TransactionId(txId), amount, T0.plusSeconds(offset))
}

class FakeExpectedPaymentRepository : ExpectedPaymentRepository {
    val payments = MutableStateFlow<List<ExpectedPayment>>(emptyList())
    val matches = MutableStateFlow<List<PaymentMatch>>(emptyList())

    override fun observeExpectedPayments(): Flow<List<ExpectedPayment>> = payments
    override fun observeMatches(): Flow<List<PaymentMatch>> = matches

    override suspend fun saveExpectedPayment(payment: ExpectedPayment) {
        payments.value = payments.value.filterNot { it.id == payment.id } + payment
    }

    override suspend fun saveMatch(match: PaymentMatch) {
        if (matches.value.none { it.id == match.id }) matches.value = matches.value + match
    }

    override suspend fun removeMatch(expectedPaymentId: ExpectedPaymentId, transactionId: TransactionId) {
        matches.value = matches.value.filterNot { it.expectedPaymentId == expectedPaymentId && it.transactionId == transactionId }
    }
}

class FakeProjectRepository : ProjectRepository {
    val projects = MutableStateFlow<List<Project>>(emptyList())

    override fun observeProjects(): Flow<List<Project>> = projects

    override suspend fun saveProject(project: Project) {
        projects.value = projects.value.filterNot { it.id == project.id } + project
    }
}
