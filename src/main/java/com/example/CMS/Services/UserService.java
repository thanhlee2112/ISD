package com.example.CMS.Services;

import com.example.CMS.DTO.EditAccountRequest;
import com.example.CMS.Exception.ConflictException;
import com.example.CMS.Exception.NotFoundException;
import com.example.CMS.Model.User;
import com.example.CMS.Repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> index() {
        return userRepository.findAll();
    }

    public User detail(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found!"));
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public void store(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new ConflictException("Username already exists");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ConflictException("Email already exists");
        }
        user.setUserId(null); // always create a new row, never overwrite an existing user
        userRepository.save(user);
    }

    /** Edits name, username, email and role. Never touches the password. */
    @Transactional
    public void update(EditAccountRequest request) {
        User user = (request.getUserId() != null
                ? userRepository.findById(request.getUserId())
                : userRepository.findByUsername(request.getUsername()))
                .orElseThrow(() -> new NotFoundException("User not found!"));

        userRepository.findByUsername(request.getUsername())
                .filter(other -> !other.getUserId().equals(user.getUserId()))
                .ifPresent(other -> { throw new ConflictException("Username already exists"); });
        userRepository.findByEmail(request.getEmail())
                .filter(other -> !other.getUserId().equals(user.getUserId()))
                .ifPresent(other -> { throw new ConflictException("Email already exists"); });

        user.setName(request.getName());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        userRepository.save(user);
    }

    public void resetPassword(User user) {
        userRepository.save(user);
    }

    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("User not found!");
        }
        userRepository.deleteById(id);
    }
}
