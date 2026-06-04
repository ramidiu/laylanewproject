package com.remitz.modules.auth.service;

import com.remitz.modules.auth.entity.PermissionEntity;
import com.remitz.modules.auth.entity.RoleEntity;
import com.remitz.common.enums.UserStatus;
import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> new SimpleGrantedAuthority(permission.getCode()))
                .distinct()
                .collect(Collectors.toList());

        boolean isActive = user.getStatus() == UserStatus.ACTIVE;

        return new User(
                user.getEmail(),
                user.getPasswordHash(),
                isActive,       // enabled
                true,           // accountNonExpired
                true,           // credentialsNonExpired
                isActive,       // accountNonLocked
                authorities
        );
    }
}
