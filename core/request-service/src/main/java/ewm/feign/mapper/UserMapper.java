package ewm.feign.mapper;

import ewm.feign.client.dto.UserResponseDto;
import ewm.feign.client.dto.UserShortDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserShortDto toUserShortDto(UserResponseDto userResponseDto);

}