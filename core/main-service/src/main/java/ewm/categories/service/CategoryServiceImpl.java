package ewm.categories.service;

import ewm.categories.dto.CategoryDto;
import ewm.categories.dto.NewCategoryDto;
import ewm.categories.mapper.CategoryMapper;
import ewm.categories.model.Category;
import ewm.categories.repository.CategoryRepository;
import ewm.exception.ConflictException;
import ewm.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    public CategoryDto createCategory(NewCategoryDto newCategoryDto) {
        if (categoryRepository.existsByName(newCategoryDto.getName())) {
            throw new ConflictException("Категория с таким названием уже существует");
        }
        Category category = categoryMapper.toCategory(newCategoryDto);
        category = categoryRepository.save(category);
        return categoryMapper.toCategoryDto(category);
    }

    @Override
    public CategoryDto updateCategory(Long catId, CategoryDto categoryDto) {
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + catId + " не найдена"));

        if (categoryRepository.existsByNameAndIdNot(categoryDto.getName(), catId)) {
            throw new ConflictException("Категория с таким названием уже существует");
        }

        category.setName(categoryDto.getName());
        category = categoryRepository.save(category);
        return categoryMapper.toCategoryDto(category);
    }

    @Override
    public void deleteCategory(Long catId) {
        if (!categoryRepository.existsById(catId)) {
            throw new NotFoundException("Категория с id=" + catId + " не найдена");
        }

        try {
            categoryRepository.deleteById(catId);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Категория используется в событиях и не может быть удалена");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDto> getCategories(Integer from, Integer size) {
        PageRequest pageRequest = PageRequest.of(from / size, size);
        return categoryRepository.findAll(pageRequest)
                .stream()
                .map(categoryMapper::toCategoryDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryDto getCategory(Long catId) {
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + catId + " не найдена"));
        return categoryMapper.toCategoryDto(category);
    }
}