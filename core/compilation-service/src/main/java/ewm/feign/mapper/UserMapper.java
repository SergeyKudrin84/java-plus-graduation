package ewm.feign.mapper;

import ewm.feign.client.dto.UserResponseDto;
import org.mapstruct.Mapper;
import ewm.feign.client.dto.UserShortDto;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserShortDto toUserShortDto(UserResponseDto userResponseDto);

}