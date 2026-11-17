package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.model.MedicineRecord
import com.example.model.PurchaseRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ExportUtils {
    fun exportPurchasesAndShare(context: Context, purchases: List<PurchaseRecord>): Result<String> {
        return try {
            val array = JSONArray()
            purchases.forEach { p ->
                array.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("productName", p.productName)
                        put("merchantName", p.merchantName)
                        put("purchasePriceInr", p.purchasePriceInr)
                        put("purchaseDate", DateAndCurrencyUtils.formatIsoDate(p.purchaseDateMillis))
                        put("invoiceNumber", p.invoiceNumber)
                        put("category", p.category)
                        put("returnDeadline", DateAndCurrencyUtils.formatIsoDate(p.returnDeadlineMillis))
                        put("warrantyDurationMonths", p.warrantyDurationMonths ?: JSONObject.NULL)
                        put("warrantyExpiry", DateAndCurrencyUtils.formatIsoDate(p.warrantyExpiryMillis))
                        put("notes", p.notes ?: "")
                    }
                )
            }
            val payload = JSONObject().apply {
                put("vault", "VAULTORA_PURCHASE_VAULT")
                put("exportedAt", DateAndCurrencyUtils.formatDate(System.currentTimeMillis()))
                put("count", purchases.size)
                put("records", array)
            }.toString(2)

            shareJsonFile(context, "vaultora_purchases_export.json", payload, "VAULTORA Purchase Vault Export")
            Result.success("Prepared export of ${purchases.size} purchase records.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exportMedicinesAndShare(context: Context, medicines: List<MedicineRecord>): Result<String> {
        return try {
            val array = JSONArray()
            medicines.forEach { m ->
                array.put(
                    JSONObject().apply {
                        put("id", m.id)
                        put("medicineName", m.medicineName)
                        put("batchNumber", m.batchNumber)
                        put("expiryDate", DateAndCurrencyUtils.formatIsoDate(m.expiryDateMillis))
                        put("purchaseDate", DateAndCurrencyUtils.formatIsoDate(m.purchaseDateMillis))
                        put("quantity", m.quantity ?: "")
                        put("storageNote", m.storageNote ?: "")
                    }
                )
            }
            val payload = JSONObject().apply {
                put("vault", "VAULTORA_MEDICINE_VAULT")
                put("exportedAt", DateAndCurrencyUtils.formatDate(System.currentTimeMillis()))
                put("count", medicines.size)
                put("records", array)
            }.toString(2)

            shareJsonFile(context, "vaultora_medicines_export.json", payload, "VAULTORA Medicine Vault Export")
            Result.success("Prepared export of ${medicines.size} medicine records.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun shareJsonFile(context: Context, fileName: String, content: String, subject: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, subject))
    }
}
