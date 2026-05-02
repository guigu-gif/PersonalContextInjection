package com.pci.controller;

import com.pci.dto.Result;
import com.pci.service.impl.PersonaServiceImpl;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/user/persona")
public class PersonaController {

    @Resource
    private PersonaServiceImpl personaService;

    @GetMapping
    public Result list() {
        return personaService.listPersona();
    }

    @PostMapping
    public Result add(@RequestBody Map<String, String> body) {
        return personaService.addFact(body);
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        return personaService.deleteFact(id);
    }

    @DeleteMapping("/clear")
    public Result clear() {
        return personaService.clearAll();
    }
}
