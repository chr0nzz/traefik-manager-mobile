package dev.chr0nzz.traefikmanager.data.model

import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeoStatus(
    val enabled: Boolean = false,
    val available: Boolean = false,
    @SerialName("db_path")
    val dbPath: String = "",
    @SerialName("db_date")
    val dbDate: String? = null,
) {
    val usable: Boolean get() = enabled && available
}

@Serializable
data class GeoLookupRequest(
    val ips: List<String>,
    val aggregate: Boolean = false,
)

@Serializable
data class GeoResult(
    @SerialName("country_code")
    val countryCode: String = "",
    @SerialName("country_name")
    val countryName: String = "",
)

@Serializable
data class GeoLookupResponse(
    val enabled: Boolean = false,
    val available: Boolean = false,
    val results: Map<String, GeoResult> = emptyMap(),
    val codes: Map<String, String> = emptyMap(),
)

data class CountryCount(
    val code: String,
    val name: String,
    val count: Int,
) {
    val flag: String get() = Countries.flag(code)
}

enum class IpClass { Loopback, LinkLocal, Private, Cgnat, Public, Unknown }

object Countries {

    private val CODE = Regex("^[A-Za-z]{2}$")

    fun flag(code: String): String {
        if (!CODE.matches(code)) return ""
        val upper = code.uppercase()
        val first = 0x1F1E6 + (upper[0].code - 'A'.code)
        val second = 0x1F1E6 + (upper[1].code - 'A'.code)
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    fun name(code: String): String {
        if (!CODE.matches(code)) return code
        val upper = code.uppercase()
        val display = Locale.Builder().setRegion(upper).build().getDisplayCountry(Locale.getDefault())
        return display.ifEmpty { upper }
    }

    fun classify(ip: String): IpClass {
        val address = ip.trim().removeSurrounding("[", "]").substringBefore('%')
        if (address.isEmpty()) return IpClass.Unknown
        if (address.contains(':')) return classifyV6(address.lowercase())
        val parts = address.split('.')
        if (parts.size != 4) return IpClass.Unknown
        val octets = parts.map { it.toIntOrNull() ?: return IpClass.Unknown }
        if (octets.any { it !in 0..255 }) return IpClass.Unknown
        val (a, b) = octets
        return when {
            a == 127 -> IpClass.Loopback
            a == 169 && b == 254 -> IpClass.LinkLocal
            a == 10 -> IpClass.Private
            a == 172 && b in 16..31 -> IpClass.Private
            a == 192 && b == 168 -> IpClass.Private
            a == 100 && b in 64..127 -> IpClass.Cgnat
            else -> IpClass.Public
        }
    }

    private fun classifyV6(address: String): IpClass = when {
        address == "::1" -> IpClass.Loopback
        address.startsWith("::ffff:") && address.contains('.') -> classify(address.substringAfterLast(':'))
        address.startsWith("fe8") || address.startsWith("fe9") ||
            address.startsWith("fea") || address.startsWith("feb") -> IpClass.LinkLocal
        address.startsWith("fc") || address.startsWith("fd") -> IpClass.Private
        address.isEmpty() -> IpClass.Unknown
        else -> IpClass.Public
    }

    fun worthLookingUp(ip: String): Boolean = when (classify(ip)) {
        IpClass.Public, IpClass.Unknown -> true
        else -> false
    }

    fun counts(codesByIp: Map<String, String>, ips: List<String>): List<CountryCount> {
        val tally = mutableMapOf<String, Int>()
        ips.forEach { ip ->
            val code = codesByIp[ip] ?: return@forEach
            tally[code] = (tally[code] ?: 0) + 1
        }
        return tally.entries
            .map { CountryCount(it.key, name(it.key), it.value) }
            .sortedWith(compareByDescending<CountryCount> { it.count }.thenBy { it.name })
    }
}
