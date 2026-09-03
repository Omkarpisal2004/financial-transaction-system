package com.financialtransaction.user.service;

import com.financialtransaction.user.entity.User;
import com.financialtransaction.user.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createuser(User user){
        userRepository.save(user);
        return user;
    }
}
