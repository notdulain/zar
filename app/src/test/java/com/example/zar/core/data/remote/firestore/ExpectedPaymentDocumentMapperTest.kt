package com.example.zar.core.data.remote.firestore

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedPaymentId
import com.example.zar.core.domain.model.ExpectedSource
import com.example.zar.core.domain.model.PaymentMatch
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.freelance.data.repository.ProjectDocumentMapper
import com.example.zar.features.freelance.domain.model.Project
import com.example.zar.features.freelance.domain.model.ProjectId
import com.example.zar.features.freelance.domain.model.ProjectStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ExpectedPaymentDocumentMapperTest {

    private fun payment(source: ExpectedSource, accountId: AccountId? = null) = ExpectedPayment(
        ExpectedPaymentId("e1"), Direction.IN, usd(500), LocalDate.of(2026, 10, 20), "Milestone 1", source, accountId, createdAt = T0,
    )

    @Test
    fun roundTripsEverySourceType() {
        listOf(
            ExpectedSource.FreelanceMilestone("p1", "e1"),
            ExpectedSource.BillOccurrence("b1", LocalDate.of(2026, 11, 1)),
            ExpectedSource.QuickNote,
            ExpectedSource.Manual,
        ).forEach { source ->
            val original = payment(source, AccountId("payoneer"))
            assertEquals(original, ExpectedPaymentDocumentMapper.expectedPaymentFromMap(ExpectedPaymentDocumentMapper.expectedPaymentToMap(original)))
        }
    }

    @Test
    fun leavesOutMissingAccountInsteadOfWritingNull() {
        val map = ExpectedPaymentDocumentMapper.expectedPaymentToMap(payment(ExpectedSource.Manual))
        assertFalse("accountId" in map)
        assertEquals(setOf("id", "direction", "amount", "dueDate", "title", "source", "status", "createdAt"), map.keys)
    }

    @Test
    fun roundTripsMatches() {
        val match = PaymentMatch(ExpectedPaymentId("e1"), TransactionId("t1"), lkr(1_000), T0)
        assertEquals(match, ExpectedPaymentDocumentMapper.matchFromMap(ExpectedPaymentDocumentMapper.matchToMap(match)))
    }

    @Test
    fun malformedDocumentsReadAsNull() {
        assertNull(ExpectedPaymentDocumentMapper.expectedPaymentFromMap(mapOf("id" to "e1")))
        assertNull(ExpectedPaymentDocumentMapper.matchFromMap(mapOf("amount" to "lots")))
        assertNull(ProjectDocumentMapper.fromMap(mapOf("id" to "p1", "name" to " ")))
    }

    @Test
    fun roundTripsProjectsWithAndWithoutClient() {
        val project = Project(ProjectId("p1"), "Website", "Acme", Currencies.USD, ProjectStatus.ACTIVE, T0)
        assertEquals(project, ProjectDocumentMapper.fromMap(ProjectDocumentMapper.toMap(project)))
        val noClient = project.copy(client = null)
        assertFalse("client" in ProjectDocumentMapper.toMap(noClient))
        assertEquals(noClient, ProjectDocumentMapper.fromMap(ProjectDocumentMapper.toMap(noClient)))
    }
}
