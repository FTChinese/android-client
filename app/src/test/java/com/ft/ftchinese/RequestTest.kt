package com.ft.ftchinese

import com.ft.ftchinese.models.Account
import com.ft.ftchinese.models.EmailUpdate
import com.ft.ftchinese.models.UserNameUpdate
import com.ft.ftchinese.util.Fetch
import com.github.javafaker.Faker
import okhttp3.Response
import org.junit.Test

class RequestTest {
    @Test
    fun loginTest() {
        val account = Account(email = "weiguo.ni@ftchinese.com", password = "12345678")

        val response = Fetch()
                .post("http://localhost:8000/users/auth")
                .setClient()
                .noCache()
                .body(account)
                .end()

        System.out.println("Response code: ${response.code()}")
        System.out.println("Response message: ${response.message()}")

        System.out.println("Is successful: ${response.isSuccessful}")

        val responseBody = response.body()?.string()
        System.out.println("Response body: $responseBody")
    }

    @Test
    fun signUp() {
        val faker = Faker()

        val email = faker.internet().emailAddress()
        val account = Account(email = email, password = "12345678")

        val response = Fetch()
                .post("http://localhost:8000/users/new")
                .setClient()
                .noCache()
                .body(account)
                .end()

       print(response)

    }

    @Test
    fun changeEmail() {
        val response = Fetch()
                .patch("http://localhost:8000/user/email")
                .noCache()
                .setUserId("e1a1f5c0-0e23-11e8-aa75-977ba2bcc6ae")
                .body(EmailUpdate("weiguo.ni@ftchinese.com"))
                .end()

        print(response)
    }

    @Test
    fun changeName() {
        val response = Fetch()
                .patch("http://localhost:8000/user/name")
                .noCache()
                .setUserId("e1a1f5c0-0e23-11e8-aa75-977ba2bcc6ae")
                .body(UserNameUpdate("Victor Nee"))
                .end()

        print(response)
    }
}

fun print(response: Response) {
    System.out.println("Response code: ${response.code()}")
    System.out.println("Response message: ${response.message()}")

    if (response.isSuccessful) {
        System.out.println("Response body: ${response.body()?.string()}")
    }
}