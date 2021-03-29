package org.commonvoice.saverio_lib.api.services

import org.commonvoice.saverio_lib.api.responseBodies.ResponseEverStats
import org.commonvoice.saverio_lib.api.responseBodies.ResponseLeaderboardPosition
import org.commonvoice.saverio_lib.api.responseBodies.ResponseVoicesToday
import org.commonvoice.saverio_lib.models.UserClient
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface CVStatsService {

    @GET("user_client")
    suspend fun getUserClient(): Response<UserClient?>

    @GET("{lang}/clips/daily_count")
    suspend fun getDailyClipsCount(
        @Path("lang") language: String
    ): Response<Int>

    @GET("{lang}/clips/votes/daily_count")
    suspend fun getDailyVotesCount(
        @Path("lang") language: String
    ): Response<Int>

    @GET("{lang}/clips/stats")
    suspend fun getEverCount(
        @Path("lang") language: String
    ): Response<List<ResponseEverStats>>

    @GET("{lang}/clips/voices")
    suspend fun getHourlyVoices(
        @Path("lang") language: String
    ): Response<List<ResponseVoicesToday>>
    
    @GET("{lang}/clips/leaderboard")
    suspend fun getRecordingsLeaderboard(
        @Path("lang") language: String
    ): Response<List<ResponseLeaderboardPosition>>

    @GET("{lang}/clips/votes/leaderboard")
    suspend fun getClipsLeaderboard(
        @Path("lang") language: String
    ): Response<List<ResponseLeaderboardPosition>>

}