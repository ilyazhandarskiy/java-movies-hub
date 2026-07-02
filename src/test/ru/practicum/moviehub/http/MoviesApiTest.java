package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {

    private static final String BASE = "http://localhost:8080";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";

    private static MoviesServer server;
    private static HttpClient client;
    private static final MoviesStore store = new MoviesStore();
    private static final Gson gson = new Gson();

    @BeforeAll
    static void beforeAll() {

        server = new MoviesServer(store, 8080);
        server.start();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @BeforeEach
    void beforeEach() {
        store.deleteAllMovies();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    //---GET_ALL---

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies")) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();

        // Обработчик тела запроса
        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        // Отправьте запрос
        HttpResponse<String> resp = client.send(req, responseBodyHandler);

        // Допишите проверку кода ответа
        assertEquals(200, resp.statusCode(), "должен вернуть 200");

        // Допишите проверку заголовка Content-Type
        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");

        assertEquals(JSON_CONTENT_TYPE, contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        // проверка, что был возвращён массив
        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
        assertEquals("[]", body, "Ожидается пустой JSON-массив");
    }

    @Test
    void getMovies_whenTwoMovie_returnsTwoMovies() throws Exception {
        store.addMovie(new Movie("Титаник", 2000));
        store.addMovie(new Movie("Титаник 2", 2002));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies")) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();


        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        HttpResponse<String> resp = client.send(request, responseBodyHandler);

        assertEquals(200, resp.statusCode(), "должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals(JSON_CONTENT_TYPE, contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        List<Movie> movies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());

        assertEquals(2, movies.size(), "GET /movies должен вернуть 2 объекта в списке");
    }

    //---POST---

    @Test
    void addMovie_withCorrectData_returnsAddedMovie() throws Exception {
        String body = "{\"title\":\"Титаник\",\"year\":2000}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies")) // !!! Добавьте правильный URI
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        Movie movie = gson.fromJson(response.body(), new TypeToken<Movie>() {
        }.getType());

        assertEquals(201, response.statusCode(), "Должен вернуться 201 HTTP status code");

        assertEquals(JSON_CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(""));

        assertTrue(movie.getId() > 0 && movie.getYear() == 2000 && movie.getTitle().equals("Титаник"),
                "Входные данные не равны выходным");
    }

    @Test
    void addMovie_withEmptyTitle_returnsError() throws Exception {
        String body = "{\"title\":\"\",\"year\":2000}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies")) // !!! Добавьте правильный URI
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        ErrorResponse errorResponse = gson.fromJson(response.body(), new TypeToken<ErrorResponse>() {
        }.getType());

        assertEquals(422, response.statusCode(), "Должен вернуться 422 HTTP status code");

        assertEquals(JSON_CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(""));

        assertEquals("Ошибка валидации", errorResponse.getError(), "Входные данные не равны выходным");
        assertFalse(errorResponse.getDetails().isEmpty(), "Массив details не должен быть пустым");
    }

    @Test
    void addMovie_withBigTitle_returnsError() throws Exception {
        String title = "Титаник".repeat(20);
        String body = "{\"title\":\"" + title + "\",\"year\":2000}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies")) // !!! Добавьте правильный URI
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        ErrorResponse errorResponse = gson.fromJson(response.body(), new TypeToken<ErrorResponse>() {
        }.getType());

        assertEquals(422, response.statusCode(), "Должен вернуться 422 HTTP status code");

        assertEquals(JSON_CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(""));

        assertEquals("Ошибка валидации", errorResponse.getError(), "Входные данные не равны выходным");
        assertFalse(errorResponse.getDetails().isEmpty(), "Массив details не должен быть пустым");
    }

    @Test
    void addMovie_withNotValidYear2030_returnsError() throws Exception {
        String body = "{\"title\":\"Титаник\",\"year\":2030}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies")) // !!! Добавьте правильный URI
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        ErrorResponse errorResponse = gson.fromJson(response.body(), new TypeToken<ErrorResponse>() {
        }.getType());

        assertEquals(422, response.statusCode(), "Должен вернуться 422 HTTP status code");

        assertEquals(JSON_CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(""));

        assertEquals("Ошибка валидации", errorResponse.getError(), "Входные данные не равны выходным");
        assertFalse(errorResponse.getDetails().isEmpty(), "Массив details не должен быть пустым");
    }

    @Test
    void addMovie_withNotValidYear1800_returnsError() throws Exception {
        String body = "{\"title\":\"Титаник\",\"year\":1800}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies")) // !!! Добавьте правильный URI
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        ErrorResponse errorResponse = gson.fromJson(response.body(), new TypeToken<ErrorResponse>() {
        }.getType());

        assertEquals(422, response.statusCode(), "Должен вернуться 422 HTTP status code");

        assertEquals(JSON_CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(""));

        assertEquals("Ошибка валидации", errorResponse.getError(), "Входные данные не равны выходным");
        assertFalse(errorResponse.getDetails().isEmpty(), "Массив details не должен быть пустым");
    }

    @Test
    void addMovie_withNotValidContentType_returnsError() throws Exception {
        String body = "{\"title\":\"Титаник\",\"year\":2000}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies")) // !!! Добавьте правильный URI
                .header("Content-Type", "application/xml; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        ErrorResponse errorResponse = gson.fromJson(response.body(), new TypeToken<ErrorResponse>() {
        }.getType());

        assertEquals(415, response.statusCode(), "Должен вернуться 415 HTTP status code");

        assertEquals(JSON_CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(""));

        assertEquals("Invalid Content-Type", errorResponse.getError(), "Входные данные не равны выходным");
    }

    @Test
    void addMovie_withNotValidJSON_returnsError() throws Exception {
        String body = "\"title\":\"Титаник\",\"year\":2000";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies")) // !!! Добавьте правильный URI
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        ErrorResponse errorResponse = gson.fromJson(response.body(), new TypeToken<ErrorResponse>() {
        }.getType());

        assertEquals(400, response.statusCode(), "Должен вернуться 400 HTTP status code");

        assertEquals(JSON_CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(""));

        assertEquals("Invalid JSON syntax", errorResponse.getError(), "Входные данные не равны выходным");
    }

    //---GET_BY_ID---
    @Test
    void getMovieById_whenExists_returnsMovieWith200() throws Exception {
        Movie movie = new Movie("Титаник", 2000);
        store.addMovie(movie);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + movie.getId())) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();

        // Обработчик тела запроса
        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        HttpResponse<String> resp = client.send(req, responseBodyHandler);

        assertEquals(200, resp.statusCode(), "должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");

        assertEquals(JSON_CONTENT_TYPE, contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        Movie createdMovie = gson.fromJson(resp.body(), Movie.class);

        assertEquals(movie.getId(), createdMovie.getId(), "Данные по Id должны быть равны");
        assertEquals(movie.getTitle(), createdMovie.getTitle(), "Данные по Title должны быть равны");
        assertEquals(movie.getYear(), createdMovie.getYear(), "Данные по Year должны быть равны");
    }

    @Test
    void getMovies_whenNotFound_returnsError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/9999")) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();

        // Обработчик тела запроса
        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        HttpResponse<String> resp = client.send(req, responseBodyHandler);

        assertEquals(404, resp.statusCode(), "должен вернуть 404");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");

        assertEquals(JSON_CONTENT_TYPE, contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        ErrorResponse errorResponse = gson.fromJson(resp.body(), ErrorResponse.class);

        assertEquals("Фильм не найден", errorResponse.getError(), "Должна вернуться ошибка: Фильм не найден");
    }

    @Test
    void getMovies_whenNotValidId_returnsError() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/lala")) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        HttpResponse<String> resp = client.send(req, responseBodyHandler);

        assertEquals(400, resp.statusCode(), "должен вернуть 400");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");

        assertEquals(JSON_CONTENT_TYPE, contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        ErrorResponse errorResponse = gson.fromJson(resp.body(), ErrorResponse.class);

        assertEquals("Некорректный ID", errorResponse.getError(), "Должна вернуться ошибка: Некорректный ID");
    }

    //---DELETE---

    @Test
    void deleteMovie_whenExists_returns204() throws Exception {
        Movie movie = new Movie("Delete", 2000);
        store.addMovie(movie);

        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + movie.getId())) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .DELETE()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        HttpResponse<String> deleteResponse = client.send(deleteRequest, responseBodyHandler);

        assertEquals(204, deleteResponse.statusCode(), "Должен вернуться 204");

        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + movie.getId())) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();

        HttpResponse<String> getResponse = client.send(getRequest, responseBodyHandler);

        assertEquals(404, getResponse.statusCode(), "Должен вернуться 404");
    }

    @Test
    void deleteMovie_whenNotExists_returns404() throws Exception {
        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/9999")) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .DELETE()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        HttpResponse<String> deleteResponse = client.send(deleteRequest, responseBodyHandler);

        assertEquals(404, deleteResponse.statusCode(), "Должен вернуться 404");
    }

    @Test
    void deleteMovie_whenNotExists_returns400() throws Exception {
        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/pupu")) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .DELETE()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        HttpResponse<String> deleteResponse = client.send(deleteRequest, responseBodyHandler);

        assertEquals(400, deleteResponse.statusCode(), "Должен вернуться 400");
    }

    //---GET_ALL_BY_YEAR---

    @Test
    void getMoviesByYear_whenTwoMovie_returnsTwoMovies() throws Exception {
        store.addMovie(new Movie("Титаник", 2000));
        store.addMovie(new Movie("Титаник 2", 2000));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2000")) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        HttpResponse<String> resp = client.send(request, responseBodyHandler);

        assertEquals(200, resp.statusCode(), "должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals(JSON_CONTENT_TYPE, contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        List<Movie> movies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());

        assertEquals(2, movies.size(), "Должен вернуть 2 объекта в списке");
    }

    @Test
    void getMoviesByYear_whenEmpty_returnsEmptyArray() throws Exception {
        store.addMovie(new Movie("Титаник", 2000));
        store.addMovie(new Movie("Титаник 2", 2000));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2001")) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        HttpResponse<String> resp = client.send(request, responseBodyHandler);

        assertEquals(200, resp.statusCode(), "должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals(JSON_CONTENT_TYPE, contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        List<Movie> movies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());

        assertEquals(0, movies.size(), "Должен вернуть 0 объекта в списке");
    }

    @Test
    void getMoviesByYear_withNonNumericYear_returns400() throws Exception {
        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/pupu")) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        HttpResponse<String> deleteResponse = client.send(deleteRequest, responseBodyHandler);

        assertEquals(400, deleteResponse.statusCode(), "Должен вернуться 400");
    }

    //---ОБЩИЕ ПРОВЕРКИ---

    @Test
    void unsupportedMethodOnMovies_returns405() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies")) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .HEAD()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        HttpResponse<String> response = client.send(request, responseBodyHandler);

        assertEquals(405, response.statusCode(), "Должен вернуться 405");
    }

    @Test
    void unsupportedMethodOnMovieById_returns405() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1")) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .HEAD()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        HttpResponse<String> response = client.send(request, responseBodyHandler);

        assertEquals(405, response.statusCode(), "Должен вернуться 405");
    }

    @Test
    void allSuccessfulResponses_haveJsonContentType() throws Exception {
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies")) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        HttpResponse<String> getResponse = client.send(getRequest, responseBodyHandler);

        assertEquals(JSON_CONTENT_TYPE, getResponse.headers().firstValue("Content-Type").orElse(""),
                "Для GET должен вернуться Content-Type:" + JSON_CONTENT_TYPE);

        String body = "{\"title\":\"TEST\",\"year\":2010}";

        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies")) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> postResponse = client.send(postRequest, responseBodyHandler);

        assertEquals(JSON_CONTENT_TYPE, postResponse.headers().firstValue("Content-Type").orElse(""),
                "Для POST должен вернуться Content-Type:" + JSON_CONTENT_TYPE);
    }

    @Test
    void errorResponses404_alwaysHaveErrorField() throws Exception {
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/9999")) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        HttpResponse<String> getResponse = client.send(getRequest, responseBodyHandler);

        JsonObject err404 = gson.fromJson(getResponse.body(), JsonObject.class);
        assertTrue(err404.has("error"), "404 должен содержать поле error");
    }

    @Test
    void errorResponses400_alwaysHaveErrorField() throws Exception {
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/aaa")) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        HttpResponse<String> getResponse = client.send(getRequest, responseBodyHandler);

        JsonObject err400 = gson.fromJson(getResponse.body(), JsonObject.class);
        assertTrue(err400.has("error"), "400 должен содержать поле error");
    }


    @Test
    void errorResponses422_alwaysHaveErrorField() throws Exception {
        String body = "{\"title\":\"TEST\",\"year\":2030}";

        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies")) // !!! Добавьте правильный URI
                .header("Content-Type", JSON_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);

        HttpResponse<String> getResponse = client.send(getRequest, responseBodyHandler);

        JsonObject err422 = gson.fromJson(getResponse.body(), JsonObject.class);
        assertTrue(err422.has("error"), "422 должен содержать поле error");
    }
}