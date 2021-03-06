package io.coster.expense_svc.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.coster.expense_svc.domain.Expense;
import io.coster.expense_svc.domain.ExpenseCategory;
import io.coster.usermanagementsvc.contract.AuthenticationResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application-integrationtest.properties")
public class ExpenseSvcIntegrationTest {

    private static final ParameterizedTypeReference<List<Expense>> EXPENSE_LIST_TYPE = new ParameterizedTypeReference<>() {};
    private static WireMockServer wireMockServer;
    private TestRestTemplate restTemplate = new TestRestTemplate();

    @LocalServerPort
    int port;

    @BeforeClass
    public static void startWireMock() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        AuthenticationResponse response = AuthenticationResponse.builder()
                .valid(true)
                .build();
        configureFor("localhost", 10001);
        wireMockServer = new WireMockServer(10001);
        wireMockServer.start();
        stubFor(post(urlEqualTo("/auth/validate")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-type", "application/json")
                .withBody(objectMapper.writeValueAsString(response))));
    }

    @Test
    public void testListExpenses_NoMonthSpecified() {
        HttpHeaders cookieHeaders = getCookieHeaders("test@test.co.uk", "1234567");

        ResponseEntity<List<Expense>> response = restTemplate.exchange(String.format("http://localhost:%d/expense/list", port),
                HttpMethod.GET, new HttpEntity<>(cookieHeaders), EXPENSE_LIST_TYPE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Expense> expenses = response.getBody();
        assertEquals(6, expenses.size());
    }

    private HttpHeaders getCookieHeaders(String userId, String token) {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.put("auth_id", Collections.singletonList(userId));
        requestHeaders.put("auth_token", Collections.singletonList(token));
        return requestHeaders;
    }

    @Test
    public void testListExpenses_WithValidMonthSpecified() {
        HttpHeaders cookieHeaders = getCookieHeaders("test@test.co.uk", "1234567");

        ResponseEntity<List<Expense>> response = restTemplate.exchange(String.format("http://localhost:%d/expense/list?month=2019-01", port),
                HttpMethod.GET, new HttpEntity<>(cookieHeaders), EXPENSE_LIST_TYPE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Expense> expenses = response.getBody();
        assertEquals(3, expenses.size());
        assertTrue(expenses.containsAll(List.of(
                new Expense(1L, "Papa Johns", 2500.0, LocalDate.of(2019, 1, 12), ExpenseCategory.EATOUT, "test@test.co.uk"),
                new Expense(2L, "Starbucks", 1200.0, LocalDate.of(2019, 1, 12), ExpenseCategory.CAFE, "test@test.co.uk"),
                new Expense(3L, "Electric Co.", 22500.0, LocalDate.of(2019, 1, 12), ExpenseCategory.UTILITIES, "test@test.co.uk")
        )));
    }

    @Test
    public void testListExpenses_WithValidMonthSpecified_ButNoExpenses() {
        HttpHeaders cookieHeaders = getCookieHeaders("test@test.co.uk", "1234567");

        ResponseEntity<List<Expense>> response = restTemplate.exchange(String.format("http://localhost:%d/expense/list?month=2019-5", port),
                HttpMethod.GET, new HttpEntity<>(cookieHeaders), EXPENSE_LIST_TYPE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Expense> expenses = response.getBody();
        assertEquals(0, expenses.size());
    }

    @Test
    public void testListExpenses_WithInvalidMonthSpecified_ReturnsBadRequest() {
        HttpHeaders cookieHeaders = getCookieHeaders("test@test.co.uk", "1234567");

        ResponseEntity<String> response = restTemplate.exchange(String.format("http://localhost:%d/expense/list?month=2019-13", port),
                HttpMethod.GET, new HttpEntity<>(cookieHeaders), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }


    @Test
    public void testCreateExpense_WithValidRequest_ThenDeleteIt() {
        Expense expense = Expense.builder()
                .location("Diner")
                .amount(5000d)
                .date(LocalDate.of(2020, 8, 19))
                .category(ExpenseCategory.EATOUT)
                .userId("test@test.co.uk")
                .build();
        HttpHeaders cookieHeaders = getCookieHeaders("test@test.co.uk", "1234567");

        // create new expense
        ResponseEntity<Expense> createResponse = restTemplate.exchange(String.format("http://localhost:%d/expense/create", port),
                HttpMethod.POST, new HttpEntity<>(expense, cookieHeaders), Expense.class);
        assertEquals(HttpStatus.OK, createResponse.getStatusCode());

        // check if it's in the table
        ResponseEntity<List<Expense>> getResponse = restTemplate.exchange(String.format("http://localhost:%d/expense/list?month=2020-08", port),
                HttpMethod.GET, new HttpEntity<>(cookieHeaders), EXPENSE_LIST_TYPE);
        assertTrue(getResponse.getBody().contains(createResponse.getBody()));

        // finally, delete it
        ResponseEntity deleteResponse = restTemplate.exchange(
                String.format("http://localhost:%d/expense/delete?id=%d", port, createResponse.getBody().getId()),
                HttpMethod.GET,
                new HttpEntity<>(cookieHeaders),
                Object.class);
        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
    }

    @Test
    public void testModifyExpense_WithValidRequest() {
        Expense modifiedExpense =  Expense.builder()
                .id(5L)
                .location("Spar")
                .amount(55000d)
                .category(ExpenseCategory.UTILITIES)
                .date(LocalDate.of(2019, 9, 12))
                .userId("test@test.co.uk")
                .build();
        HttpHeaders cookieHeaders = getCookieHeaders("test@test.co.uk", "1234567");

        restTemplate.exchange(String.format("http://localhost:%d/expense/modify", port),
                HttpMethod.POST, new HttpEntity<>(modifiedExpense, cookieHeaders), Expense.class);

        ResponseEntity<List<Expense>> getResponse = restTemplate.exchange(String.format("http://localhost:%d/expense/list", port),
                HttpMethod.GET, new HttpEntity<>(cookieHeaders), EXPENSE_LIST_TYPE);

        assertTrue(getResponse.getBody().contains(modifiedExpense));
    }

    @Test
    public void testModifyExpense_WithInvalidRequest() {
        Expense expenseThatDoesNotExist =  Expense.builder()
                .id(5400L)
                .location("Electric Co.")
                .amount(55000d)
                .category(ExpenseCategory.UTILITIES)
                .date(LocalDate.of(2019, 1, 12))
                .userId("test@test.co.uk")
                .build();
        HttpHeaders cookieHeaders = getCookieHeaders("test@test.co.uk", "1234567");

        ResponseEntity<String> modifyResponse = restTemplate.exchange(String.format("http://localhost:%d/expense/modify", port),
                HttpMethod.POST, new HttpEntity<>(expenseThatDoesNotExist, cookieHeaders), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, modifyResponse.getStatusCode());

        ResponseEntity<List<Expense>> getResponse = restTemplate.exchange(String.format("http://localhost:%d/expense/list", port),
                HttpMethod.GET, new HttpEntity<>(cookieHeaders), EXPENSE_LIST_TYPE);
        assertFalse(getResponse.getBody().contains(expenseThatDoesNotExist));
    }

    @AfterClass
    public static void shutdownWireMock() {
        wireMockServer.stop();
    }
}
