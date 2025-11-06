package org.glodean.constants.web.endpoints;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/ping")
public final class PingController {
    @GetMapping
    public Mono<String> ping(){
        return Mono.just("{\"message\":\"Ping!\"}");
    }
}
