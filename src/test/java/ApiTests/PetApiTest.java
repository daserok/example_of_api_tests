package ApiTests;

import io.qameta.allure.Step;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.ErrorLoggingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openapitools.client.ApiClient;
import org.openapitools.client.api.PetApi;
import org.openapitools.client.model.Category;
import org.openapitools.client.model.Pet;
import org.openapitools.client.model.Tag;


import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static io.restassured.config.ObjectMapperConfig.objectMapperConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.openapitools.client.GsonObjectMapper.gson;
import static org.openapitools.client.ResponseSpecBuilders.shouldBeCode;
import static org.openapitools.client.ResponseSpecBuilders.validatedWith;

@DisplayName("Проверка API Pet")
public class PetApiTest {
    private PetApi api;

    @BeforeEach
    @Step("Создание Client API")
    public void createApi() {
        api = ApiClient.api(ApiClient.Config.apiConfig().reqSpecSupplier(
                () -> new RequestSpecBuilder()
                        .setConfig(config().objectMapperConfig(objectMapperConfig().defaultObjectMapper(gson())))
                        .addFilter(new ErrorLoggingFilter())
                        .addFilter(new AllureRestAssured())
                        .setBaseUri("https://petstore.swagger.io/v2"))).pet();
    }


    private static Stream<Arguments> structureOfPets() {
        return Stream.of(
                arguments(new Pet().id(1L)
                        .name("test")
                        .category(new Category().id(1L).name("Doge"))
                        .addPhotoUrlsItem("photo")
                        .tags(Collections.singletonList(new Tag().id(1L).name("Label")))
                        .status(Pet.StatusEnum.AVAILABLE)
                ),
                arguments(new Pet().id(5L)
                        .name("こんにちは")
                        .category(new Category().id(10L).name("Sam"))
                        .addPhotoUrlsItem("newPhoto")
                        .tags(Collections.singletonList(new Tag().id(3L).name("Label")))
                        .status(Pet.StatusEnum.PENDING)
                ),
                arguments(new Pet().id(Long.MAX_VALUE)
                        .name("Питомец")
                        .category(new Category().id(100L).name("Сэм"))
                        .addPhotoUrlsItem("Фото")
                        .tags(Collections.singletonList(new Tag().id(3L).name("Питомец")))
                        .status(Pet.StatusEnum.SOLD)
                )
        );
    }

    @DisplayName("Создание нового питомца")
    @ParameterizedTest(name = "Создание нового питомца {0}")
    @MethodSource("structureOfPets")
    public void shouldHave200AfterCreationOfPet(Pet pet) {
        step("Описание структуры нового питомца", () -> {
            step("Выполнение и проверка запроса на создание питомца", () -> {
                Pet createdPet = api.addPet()
                        .body(pet)
                        .execute(validatedWith(shouldBeCode(SC_OK)))
                        .then()
                        .assertThat()
                        .body("name", equalTo(pet.getName()))
                        .body("category.name", equalTo(Objects.requireNonNull(pet.getCategory()).getName()))
                        .body("photoUrls", equalTo(pet.getPhotoUrls()))
                        .body("tags.name[0]", equalTo(Objects.requireNonNull(pet.getTags()).get(0).getName()))
                        .body("status", equalTo(Objects.requireNonNull(pet.getStatus()).getValue()))
                        .extract()
                        .as(Pet.class);
                assertThat(createdPet.getId(), equalTo(pet.getId()));
                assertThat(createdPet.getCategory().getId(), equalTo(pet.getCategory().getId()));
                assertThat(createdPet.getTags().get(0).getId(), equalTo(pet.getTags().get(0).getId()));
            });
        });
    }

    @ParameterizedTest(name = "Поиск питомца с идентификатором {0}")
    @ValueSource(longs = {99999L, 543534L, 777777777777L})
    public void shouldHave200AfterGetPetInfo(long id) {
        step("Создание питомца", () -> {
            api.addPet().body(new Pet().id(id).name("John"))
                    .execute(validatedWith(shouldBeCode(SC_OK)));
        });
        step("Поиск ранее созданного питомца", () -> {
            Pet pet = api.getPetById().petIdPath(id)
                    .execute(validatedWith(shouldBeCode(SC_OK)))
                    .then()
                    .extract()
                    .as(Pet.class);
            step("Проверка информации о питомце", () -> {
                assertThat(pet.getId(), equalTo(id));
                assertThat(pet.getName(), equalTo("John"));
            });
        });
    }

    @ParameterizedTest(name = "Поиск питомцев со статусом {0}")
    @EnumSource(Pet.StatusEnum.class)
    public void shouldHaveNonEmptyListWhenFindToStatus(Pet.StatusEnum status) {
        Pet pet = new Pet().name("Johny").status(status);
        step("Создание питомца", () -> {
            api.addPet().body(pet)
                    .execute(validatedWith(shouldBeCode(SC_OK)));
        });
        step("Получение массива питомцев по статусу", () -> {
            List<Pet> listOfPetsByStatus = api.findPetsByStatus().statusQuery(status)
                    .executeAs(validatedWith(shouldBeCode(SC_OK)));
            step("Проверка информации полученной о питомцах", () -> {
                step("Размер списка >= 1", () -> {
                    assertThat(listOfPetsByStatus.size(), greaterThan(0));
                });
                step("Проверка, что все элементы массива имеют нужный статус", () -> {
                    assertThat((int) listOfPetsByStatus.stream()
                            .filter(p -> Objects.equals(p.getStatus(), status))
                            .count(), equalTo(listOfPetsByStatus.size()));
                });
                step("Проверка наличия ранее созданного элемента в списке питомцев", () -> {
                    assertThat((int) listOfPetsByStatus.stream()
                            .filter(p -> Objects.equals(p.getName(), pet.getName()))
                            .count(), greaterThan(0));
                });
            });
        });
    }

}
