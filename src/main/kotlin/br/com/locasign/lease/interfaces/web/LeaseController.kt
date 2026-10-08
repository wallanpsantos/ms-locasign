package br.com.locasign.lease.interfaces.web

import br.com.locasign.lease.app.queries.GetLease
import br.com.locasign.lease.app.usecases.RegisterLease
import br.com.locasign.lease.domain.models.LeaseStatus
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.lease.interfaces.web.dto.request.CreateLeaseRequest
import br.com.locasign.lease.interfaces.web.dto.response.LeaseCreatedResponse
import br.com.locasign.lease.interfaces.web.dto.response.LeaseResponse
import br.com.locasign.lease.interfaces.web.mappers.toCommand
import br.com.locasign.lease.interfaces.web.mappers.toResponse
import br.com.locasign.lease.interfaces.web.openapi.LeaseApi
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/api/v1/leases")
class LeaseController(
    private val registerLease: RegisterLease,
    private val getLease: GetLease,
) : LeaseApi {

    @PostMapping
    override fun register(@Valid @RequestBody request: CreateLeaseRequest): ResponseEntity<LeaseCreatedResponse> {
        val id = registerLease.execute(request.toCommand())
        val location =
            ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id.toString()).toUri()
        return ResponseEntity.created(location).body(LeaseCreatedResponse(id.toString(), LeaseStatus.REGISTERED.name))
    }

    @GetMapping("/{leaseId}")
    override fun get(@PathVariable leaseId: String): LeaseResponse =
        getLease.execute(LeaseId.parse(leaseId)).toResponse()
}
