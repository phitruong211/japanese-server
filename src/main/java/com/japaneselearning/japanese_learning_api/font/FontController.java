package com.japaneselearning.japanese_learning_api.font;

import com.japaneselearning.japanese_learning_api.security.CurrentUser;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fonts")
public class FontController {
    private final FontService service;
    public FontController(FontService service) { this.service = service; }

    @GetMapping
    ResponseEntity<FontService.FontList> list(Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(CurrentUser.id(auth)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<FontService.FontInfo> upload(Authentication auth, @RequestParam("file") MultipartFile file, @RequestParam(value = "name", required = false) String name) throws IOException {
        var result = service.upload(CurrentUser.id(auth), name, file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).cacheControl(CacheControl.noStore()).body(result.font());
    }

    /** Only the owner can read a font's bytes; the file is never served from a public URL. */
    @GetMapping("/{id}/file")
    ResponseEntity<byte[]> file(Authentication auth, @PathVariable UUID id) {
        var file = service.file(CurrentUser.id(auth), id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("font/" + file.format()))
                .header("X-Content-Type-Options", "nosniff")
                // Never cached: a browser cache is keyed by URL, so it would outlive a deletion and cross accounts on a shared device.
                .cacheControl(CacheControl.noStore()).body(file.data());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication auth, @PathVariable UUID id) { service.delete(CurrentUser.id(auth), id); }
}
