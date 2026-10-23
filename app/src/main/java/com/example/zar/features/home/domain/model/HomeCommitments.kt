package com.example.zar.features.home.domain.model

import com.example.zar.features.bills.domain.model.NextBill
import com.example.zar.features.freelance.domain.model.Outstanding

/**
 * What Home reminds the user about, separate from Received and Spent.
 *
 * [outstanding] is what clients still owe, one total per currency and never converted. It only
 * counts expected client money, so it never enters Received. [nextBill] is the next expected
 * bill date; a bill with no linked payment is shown as expected, never as definitely unpaid.
 */
data class HomeCommitments(
    val outstanding: Outstanding,
    val nextBill: NextBill?,
)
