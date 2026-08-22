package `in`.callsentry.app.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.CallScreeningService.CallResponse
import `in`.callsentry.app.CallSentryApp
import `in`.callsentry.app.core.Contacts
import `in`.callsentry.app.core.Decision
import `in`.callsentry.app.core.DecisionEngine
import `in`.callsentry.app.core.IdentityResolver
import `in`.callsentry.app.core.Phone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * On-device call screening. Every decision is made locally from the local
 * registry, rules, relationships and community data — no network involved.
 */
class SentryScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(details: Call.Details) {
        val raw = details.handle?.schemeSpecificPart
        val number = Phone.digits(raw)

        if (number == null || Phone.isEmergency(number) ||
            details.callDirection == Call.Details.DIRECTION_OUTGOING
        ) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        val app = application as CallSentryApp
        scope.launch {
            val contact = raw?.let { Contacts.lookup(application, it) }
            val identity = IdentityResolver(app.db).resolve(number, contact)
            val (decision, reason) = DecisionEngine(app.db, app.prefs).decide(number, identity)

            val response = when (decision) {
                Decision.REJECT ->
                    CallResponse.Builder().setDisallowCall(true).setRejectCall(true).build()
                Decision.SILENCE ->
                    CallResponse.Builder().setSilenceCall(true).build()
                Decision.ALLOW ->
                    CallResponse.Builder().build()
            }
            respondToCall(details, response)

            val evidence = identity.evidence.joinToString("\n") { "${it.source}:: ${it.statement}" }
            app.db.insertCall(
                System.currentTimeMillis(), number, identity.level.name, identity.name,
                decision.name, reason, evidence
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
