package com.japaneselearning.japanese_learning_api.learning;
import com.japaneselearning.japanese_learning_api.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/users/me/learning")
public class LearningController {
 private final LearningService service;
 public LearningController(LearningService service){this.service=service;}
 @GetMapping public LearningDtos.Snapshot get(Authentication auth){return service.get(CurrentUser.id(auth));}
 @PutMapping public LearningDtos.Snapshot put(Authentication auth,@Valid @RequestBody LearningDtos.Update request){return service.put(CurrentUser.id(auth),request);}
 @PostMapping("/guest") public LearningDtos.Result guest(Authentication auth,@Valid @RequestBody LearningDtos.Guest request){return service.migrate(CurrentUser.id(auth),request);}
}
