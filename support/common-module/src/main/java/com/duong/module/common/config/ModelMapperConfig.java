package com.duong.module.common.config;

import java.util.List;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelMapperConfig {

	@Bean
	@ConditionalOnMissingBean
	ModelMapper modelMapper(List<Converter<?, ?>> converters, List<PropertyMap<?, ?>> propertyMaps) {
		ModelMapper modelMapper = new ModelMapper();
		modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
		converters.forEach(modelMapper::addConverter);
		propertyMaps.forEach(modelMapper::addMappings);
		return modelMapper;
	}

}
