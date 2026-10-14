package com.japaneselearning.japanese_learning_api.notes;

import com.japaneselearning.japanese_learning_api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/card-notes")
public class PersonalCardNoteController {
    private final PersonalCardNoteService service;
    public PersonalCardNoteController(PersonalCardNoteService service) { this.service = service; }
    @GetMapping("/{key}")
    public ResponseEntity<PersonalCardNoteService.Note> get(Authentication auth, @PathVariable String key) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(CurrentUser.id(auth), key));
    }
    @PutMapping("/{key}")
    public PersonalCardNoteService.Note put(Authentication auth, @PathVariable String key, @Valid @RequestBody PersonalCardNoteService.Update request) {
        return service.put(CurrentUser.id(auth), key, request);
    }
}
