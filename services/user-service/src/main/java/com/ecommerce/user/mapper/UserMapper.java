package com.ecommerce.user.mapper;

import com.ecommerce.user.api.dto.AddressResponse;
import com.ecommerce.user.api.dto.UserProfileResponse;
import com.ecommerce.user.domain.Address;
import com.ecommerce.user.domain.UserProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "fullName",
             expression = "java(profile.getFullName())")
    @Mapping(target = "gender",
             expression = "java(profile.getGender() != null ? profile.getGender().name() : null)")
    @Mapping(target = "isActive",
             source = "isActive")
    UserProfileResponse toResponse(UserProfile profile);

    @Mapping(target = "isDefault", source = "isDefault")
    AddressResponse toAddressResponse(Address address);

    List<AddressResponse> toAddressResponseList(List<Address> addresses);
}
