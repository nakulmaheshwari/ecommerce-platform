package com.ecommerce.user.service;

import com.ecommerce.common.exception.DuplicateResourceException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.user.api.dto.*;
import com.ecommerce.user.domain.*;
import com.ecommerce.user.mapper.UserMapper;
import com.ecommerce.user.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final AddressRepository addressRepository;
    private final OutboxRepository outboxRepository;
    private final UserMapper userMapper;

    @Transactional
    public UserProfileResponse createProfile(UUID keycloakId, String email,
                                              String firstName, String lastName,
                                              String phoneNumber) {
        return userProfileRepository.findByKeycloakId(keycloakId)
            .map(existing -> {
                log.info("Profile already exists for keycloakId={}", keycloakId);
                return userMapper.toResponse(existing);
            })
            .orElseGet(() -> {
                if (userProfileRepository.existsByEmail(email)) {
                    throw new DuplicateResourceException("UserProfile", "email", email);
                }

                UserProfile profile = UserProfile.builder()
                    .keycloakId(keycloakId)
                    .email(email)
                    .firstName(firstName)
                    .lastName(lastName)
                    .phoneNumber(phoneNumber)
                    .isActive(true)
                    .build();

                userProfileRepository.save(profile);

                log.info("User profile created userId={} email={}",
                    profile.getId(), email);
                return userMapper.toResponse(profile);
            });
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(UUID keycloakId) {
        UserProfile profile = findByKeycloakIdOrThrow(keycloakId);
        return userMapper.toResponse(profile);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfileById(UUID userId) {
        UserProfile profile = userProfileRepository.findById(userId)
            .orElseThrow(() ->
                new ResourceNotFoundException("UserProfile", userId.toString()));
        return userMapper.toResponse(profile);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID keycloakId,
                                              UpdateProfileRequest request) {
        UserProfile profile = findByKeycloakIdOrThrow(keycloakId);

        if (request.firstName()   != null) profile.setFirstName(request.firstName());
        if (request.lastName()    != null) profile.setLastName(request.lastName());
        if (request.phoneNumber() != null) profile.setPhoneNumber(request.phoneNumber());
        if (request.dateOfBirth() != null) profile.setDateOfBirth(request.dateOfBirth());
        if (request.avatarUrl()   != null) profile.setAvatarUrl(request.avatarUrl());
        if (request.gender()      != null) {
            try {
                profile.setGender(Gender.valueOf(request.gender().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid gender value: " + request.gender());
            }
        }

        userProfileRepository.save(profile);

        outboxRepository.save(OutboxEvent.builder()
            .aggregateType("UserProfile")
            .aggregateId(profile.getId())
            .eventType("user.profile-updated")
            .payload(Map.of(
                "userId",    profile.getId().toString(),
                "email",     profile.getEmail(),
                "firstName", profile.getFirstName(),
                "lastName",  profile.getLastName()
            ))
            .build());

        log.info("Profile updated userId={}", profile.getId());
        return userMapper.toResponse(profile);
    }

    @Transactional
    public UserProfileResponse updatePreference(UUID keycloakId,
                                                 UpdatePreferenceRequest request) {
        UserProfile profile = findByKeycloakIdOrThrow(keycloakId);
        profile.updatePreference(request.key(), request.value());
        userProfileRepository.save(profile);

        log.info("Preference updated userId={} key={}", profile.getId(), request.key());
        return userMapper.toResponse(profile);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(UUID keycloakId) {
        UserProfile profile = findByKeycloakIdOrThrow(keycloakId);
        List<Address> addresses = addressRepository
            .findByUserProfileIdAndIsDeletedFalseOrderByIsDefaultDescCreatedAtAsc(
                profile.getId());
        return userMapper.toAddressResponseList(addresses);
    }

    @Transactional
    public AddressResponse addAddress(UUID keycloakId, AddressRequest request) {
        UserProfile profile = findByKeycloakIdOrThrow(keycloakId);

        long addressCount = addressRepository
            .findByUserProfileIdAndIsDeletedFalseOrderByIsDefaultDescCreatedAtAsc(
                profile.getId()).size();
        if (addressCount >= 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Maximum 10 addresses allowed per account");
        }

        boolean shouldBeDefault = request.makeDefault() ||
            !addressRepository.existsByUserProfileIdAndIsDeletedFalse(profile.getId());

        if (shouldBeDefault) {
            addressRepository.unsetAllDefaults(profile.getId());
        }

        Address address = Address.builder()
            .userProfile(profile)
            .label(request.label() != null ? request.label() : "Home")
            .fullName(request.fullName())
            .phoneNumber(request.phoneNumber())
            .line1(request.line1())
            .line2(request.line2())
            .city(request.city())
            .state(request.state())
            .pincode(request.pincode())
            .country(request.country() != null ? request.country() : "India")
            .isDefault(shouldBeDefault)
            .build();

        addressRepository.save(address);

        log.info("Address added userId={} addressId={} isDefault={}",
            profile.getId(), address.getId(), shouldBeDefault);
        return userMapper.toAddressResponse(address);
    }

    @Transactional
    public AddressResponse updateAddress(UUID keycloakId, UUID addressId,
                                          AddressRequest request) {
        UserProfile profile = findByKeycloakIdOrThrow(keycloakId);

        Address address = addressRepository
            .findByIdAndUserProfileIdAndIsDeletedFalse(addressId, profile.getId())
            .orElseThrow(() ->
                new ResourceNotFoundException("Address", addressId.toString()));

        if (request.label()       != null) address.setLabel(request.label());
        if (request.fullName()    != null) address.setFullName(request.fullName());
        if (request.phoneNumber() != null) address.setPhoneNumber(request.phoneNumber());
        if (request.line1()       != null) address.setLine1(request.line1());
        if (request.line2()       != null) address.setLine2(request.line2());
        if (request.city()        != null) address.setCity(request.city());
        if (request.state()       != null) address.setState(request.state());
        if (request.pincode()     != null) address.setPincode(request.pincode());
        if (request.country()     != null) address.setCountry(request.country());

        if (request.makeDefault() && !address.getIsDefault()) {
            addressRepository.unsetAllDefaults(profile.getId());
            address.setIsDefault(true);
        }

        addressRepository.save(address);

        log.info("Address updated userId={} addressId={}", profile.getId(), addressId);
        return userMapper.toAddressResponse(address);
    }

    @Transactional
    public AddressResponse setDefaultAddress(UUID keycloakId, UUID addressId) {
        UserProfile profile = findByKeycloakIdOrThrow(keycloakId);

        Address address = addressRepository
            .findByIdAndUserProfileIdAndIsDeletedFalse(addressId, profile.getId())
            .orElseThrow(() ->
                new ResourceNotFoundException("Address", addressId.toString()));

        addressRepository.unsetAllDefaults(profile.getId());
        address.setIsDefault(true);
        addressRepository.save(address);

        log.info("Default address set userId={} addressId={}",
            profile.getId(), addressId);
        return userMapper.toAddressResponse(address);
    }

    @Transactional
    public void deleteAddress(UUID keycloakId, UUID addressId) {
        UserProfile profile = findByKeycloakIdOrThrow(keycloakId);

        Address address = addressRepository
            .findByIdAndUserProfileIdAndIsDeletedFalse(addressId, profile.getId())
            .orElseThrow(() ->
                new ResourceNotFoundException("Address", addressId.toString()));

        address.softDelete();
        addressRepository.save(address);

        log.info("Address soft-deleted userId={} addressId={}",
            profile.getId(), addressId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDefaultAddressForOrder(UUID userId) {
        UserProfile profile = userProfileRepository
            .findByIdWithAddresses(userId)
            .orElseThrow(() ->
                new ResourceNotFoundException("UserProfile", userId.toString()));

        return profile.getDefaultAddress()
            .map(addr -> Map.<String, Object>of(
                "fullName",    addr.getFullName(),
                "phoneNumber", addr.getPhoneNumber(),
                "line1",       addr.getLine1(),
                "line2",       addr.getLine2() != null ? addr.getLine2() : "",
                "city",        addr.getCity(),
                "state",       addr.getState(),
                "pincode",     addr.getPincode(),
                "country",     addr.getCountry()
            ))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No default address found. Please add an address before ordering."));
    }

    private UserProfile findByKeycloakIdOrThrow(UUID keycloakId) {
        return userProfileRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() ->
                new ResourceNotFoundException("UserProfile", keycloakId.toString()));
    }
}
