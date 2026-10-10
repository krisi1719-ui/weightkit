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
                    .put("pricePerKg", x.pricePerKg ?: JSONObject.NULL))
            }
            a.put(JSONObject().put("id", l.id).put("name", l.name).put("created", l.created)
                .put("wastePct", l.wastePct).put("markupPct", l.markupPct).put("items", items))
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
                        if (x.isNull("pricePerKg")) null else x.getDouble("pricePerKg"))
                }
                CutList(o.getString("id"), o.getString("name"), o.getLong("created"),
                    o.getDouble("wastePct"), o.getDouble("markupPct"), items)
            }.getOrNull()
        }
    }
}
