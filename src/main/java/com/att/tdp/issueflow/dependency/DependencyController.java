package com.att.tdp.issueflow.dependency;

import com.att.tdp.issueflow.dependency.dto.AddDependencyRequest;
import com.att.tdp.issueflow.dependency.dto.DependencyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Ticket dependency endpoints, per the README contract. */
@RestController
@RequestMapping("/tickets/{ticketId}/dependencies")
@RequiredArgsConstructor
public class DependencyController {

    private final DependencyService dependencyService;

    @PostMapping
    public void addDependency(@PathVariable Long ticketId,
                              @Valid @RequestBody AddDependencyRequest request) {
        dependencyService.addDependency(ticketId, request.blockedBy());
    }

    @GetMapping
    public List<DependencyResponse> getDependencies(@PathVariable Long ticketId) {
        return dependencyService.getDependencies(ticketId);
    }

    @DeleteMapping("/{blockerId}")
    public void removeDependency(@PathVariable Long ticketId, @PathVariable Long blockerId) {
        dependencyService.removeDependency(ticketId, blockerId);
    }
}
