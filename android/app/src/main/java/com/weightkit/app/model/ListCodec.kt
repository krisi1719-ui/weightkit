package com.weightkit.app.model

import org.json.JSONArray
import org.json.JSONObject

/** JSON (de)serialisation of saved lists. Unknown / broken entries are skipped, never crash. */
object ListCodec {
    fun encode(lists: List<CutList>): String {
        val a = JSONArray()
        for (l in lists) {
            val items = JSONArray()
            for (x in l.items) {
                items.put(JSONObject().put("id", x.id).put("shape", x.shape).put("material", x.material)
                    .put("size", x.size).put("qty", x.qty).put("pieceKg", x.pieceKg)
                    .put("pricePerKg", x.pricePerKg ?: JSONObject.NULL)
                    .put("priceUnit", x.priceUnit.name).put("pieceM", x.pieceM ?: JSONObject.NULL))
            }
            a.put(JSONObject().put("id", l.id).put("name", l.name).put("created", l.created)
                .put("wastePct", l.wastePct).put("markupPct", l.markupPct).put("items", items)
                .put("client", l.client).put("quoteNo", l.quoteNo).put("vatPct", l.vatPct)
                .put("validDays", l.validDays).put("notes", l.notes))
        }
        return a.toString()
    }

    fun decode(s: String): List<CutList> {
        val a = runCatching { JSONArray(s) }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            runCatching {
                val o = a.getJSONObject(i)
                val ia = o.getJSONArray("items")
                val items = (0 until ia.length()).map { j ->
                    val x = ia.getJSONObject(j)
                    ListItem(x.getString("id"), x.getString("shape"), x.getString("material"), x.getString("size"),
                        x.getInt("qty"), x.getDouble("pieceKg"),
                        if (x.isNull("pricePerKg")) null else x.getDouble("pricePerKg"),
                        runCatching { PriceUnit.valueOf(x.optString("priceUnit", "KG")) }.getOrDefault(PriceUnit.KG),
                        if (!x.has("pieceM") || x.isNull("pieceM")) null else x.getDouble("pieceM"))
                }
                CutList(o.getString("id"), o.getString("name"), o.getLong("created"),
                    o.getDouble("wastePct"), o.getDouble("markupPct"), items,
                    client = o.optString("client"), quoteNo = o.optString("quoteNo"), vatPct = o.optDouble("vatPct", 0.0),
                    validDays = o.optInt("validDays", 0), notes = o.optString("notes"))
            }.getOrNull()
        }
    }
}
