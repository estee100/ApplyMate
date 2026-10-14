package com.applymate.controller;

import com.applymate.service.CurrentUser;
import com.applymate.service.RateLimiter;
import com.applymate.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class SearchController {

    private final SearchService search;
    private final RateLimiter limiter;
    private final CurrentUser currentUser;

    public SearchController(SearchService search, RateLimiter limiter, CurrentUser currentUser) {
        this.search = search;
        this.limiter = limiter;
        this.currentUser = currentUser;
    }

    /** Search-as-you-type for the command palette: generous, but bounded. */
    @GetMapping("/search")
    public SearchService.Results search(@RequestParam(defaultValue = "") String q) {
        limiter.check("search:" + currentUser.id(), 120, Duration.ofMinutes(1), "Searching too fast. Slow down a little.");
        return search.search(q.length() > 200 ? q.substring(0, 200) : q);
    }
}
