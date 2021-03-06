package com.mkm.statsdontlie.nba

import org.scalatra._
import org.scalatra.json._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{parse, compact}

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.nio.charset.StandardCharsets

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

class NbaServlet extends NbaStack with FutureSupport {

  protected implicit def executor = ExecutionContext.global

/////////////////////////// external API //////////////////////////////////

  get("/nba") {
    <html>
      <body>
        <h1>Try <a href="/nba/team/matchup/random">random</a> team matchup!</h1>
        <h1>Try <a href="/nba/player/matchup/random">random</a> player matchup!</h1>
	
	<h2><a href="/nba/team/matchup">Create your own team matchup. </a></h2>

	<br/>

	<h2><a href="/nba/player/matchup">Create your own player matchup. </a></h2>
      </body>
    </html>
  }

/////////////////////// Info ///////////////////////////////

  get("/api/v1/nba/team/:team") {
    val teamName = params("team")

    StatsNBA.teamByName(teamName) match {
      case JNothing => compact(JNothing)
      case jValue: JValue => compact(jValue)
    }
      
  }

  get("/api/v1/nba/player/:player") {
    val playerName = params("player")

    StatsNBA.playerByName(playerName) match {
      case JNothing => compact(JNothing)
      case jValue: JValue => compact(jValue)
    }
      
  }

//////////////////////// Matchup ////////////////////////////////

  get("/nba/team/matchup") {
    contentType="text/html"

    // TODO: pass List[Team] to template instead
    layoutTemplate("team_matchup.html", "teams" -> StatsNBA.teamNameToId.keys.toList)
  }

  get("/nba/team/matchup/random") {
    contentType="text/html"

    val randomIdxOne = scala.util.Random.nextInt(StatsNBA.teams.size)
    val randomIdxTwo = if (randomIdxOne == 0) (randomIdxOne + 1) else (randomIdxOne - 1)

    val matchup = List(
      StatsNBA.teams(randomIdxOne),
      StatsNBA.teams(randomIdxTwo))

    layoutTemplate("team_random_matchup.html", "matchup" -> matchup)
  }

  post("/api/v1/nba/team/matchup") {

    val team1Name = params("team1")
    val team2Name = params("team2")

    val teamWonJValue = for {
      team1Id <- StatsNBA.teamId(team1Name)
      team2Id <- StatsNBA.teamId(team2Name)
    } yield TeamMatchup.resultJV((Team(team1Name, team2Id), Team(team2Name, team2Id)))

    teamWonJValue match {
      case Some(jValue) => compact(jValue)
      case None => compact(JNothing)
    }
  }

 ////////////////////////////

  get("/nba/player/matchup") {
    contentType="text/html"

    // TODO: pass List[Player] to template instead
    layoutTemplate("player_matchup.html", "players" -> StatsNBA.playerNameToId.keys.toList)
  }

  get("/nba/player/matchup/random") {
    contentType="text/html"

    val randomIdxOne = scala.util.Random.nextInt(StatsNBA.players.size)
    val randomIdxTwo = if (randomIdxOne == 0) (randomIdxOne + 1) else (randomIdxOne - 1)

    val matchup = List(
      StatsNBA.players(randomIdxOne),
      StatsNBA.players(randomIdxTwo))

    layoutTemplate("player_random_matchup.html", "matchup" -> matchup)
  }

  post("/api/v1/nba/player/matchup") {

    val player1Name = params("player1")
    val player2Name = params("player2")

    val playerWonJValue = for {
      player1Id <- StatsNBA.playerId(player1Name)
      player2Id <- StatsNBA.playerId(player2Name)
    } yield PlayerMatchup.result((Player(player1Name, player1Id), Player(player2Name, player2Id)))

    playerWonJValue match {
      case Some(jValue) => compact(jValue)
      case None => compact(JNothing)
    }
  }

//////////////////////// Tournament ////////////////////////////////

  post("/api/v1/nba/team/tournament") {
    val teams = for {
      team <- multiParams("team").toList
      teamId <- StatsNBA.teamId(team)
    } yield Team(team, teamId)

    Tournament.winner(teams) match {
      case Some(team) => compact(("result" -> team.name) ~ ("time" -> Instant.now.toString))
      case None => compact(JNothing)
    }
  }

}

///////////////////////////////////////////////////////////////////////

case class Team(val name: String, val teamId: String)

case class Player(val name: String, val playerId: String)

//////////////////////////////////////////////////////////////////////

object TeamMatchup {
  def result(pair: Pair[Team, Team]): Option[Team] = {
    /*
     * TODO:
     * 1) Query stats.nba.com in order to get information about two teams
     * 2) Perform analytics to determine the result
     */
    val team1Js = StatsNBA.team(pair._1)
    val team2Js = StatsNBA.team(pair._2)

    val prior = Instant.now

    // TODO: add algorithm for calculating the winner
    Some(pair._1)
  }

  def resultJV(pair: Pair[Team, Team]): JValue = {
    /*
     * TODO:
     * 1) Query stats.nba.com in order to get information about two teams
     * 2) Perform analytics to determine the result
     */
    val team1Js = StatsNBA.team(pair._1)
    val team2Js = StatsNBA.team(pair._2)

    val prior = Instant.now

    // TODO: add algorithm for calculating the winner
    var winner: Option[Team] = Some(pair._1)

    winner match {
      case Some(winner) => ("response" -> winner.name) ~ ("latency" -> ChronoUnit.MILLIS.between(prior, Instant.now))
      case None => JNothing
    }
  }
}

object PlayerMatchup {

  case class PlayerStats(val points: Option[Double], val assists: Option[Double], val rebounds: Option[Double])

  // TODO: add more parameters to player's mojo calculation
  def playerMojo(playerStats: PlayerStats): Option[Double] = {
    for {
      playerPoints <- playerStats.points
      playerRebounds <- playerStats.rebounds
      playerAssists <- playerStats.assists
    } yield (playerPoints + playerAssists + playerRebounds)
  }

  def result(pair: Pair[Player, Player]): JValue = {
    /*
     * 1) Query stats.nba.com in order to get information about two players
     * 2) Perform analytics to determine the result
     */
    val player1Js = StatsNBA.player(pair._1)
    val player2Js = StatsNBA.player(pair._2)

    val prior = Instant.now

    val player1Stats = playerStats(player1Js)
    val player2Stats = playerStats(player2Js)

    val winnerOpt = for {
      player1Mojo <- playerMojo(player1Stats)
      player2Mojo <- playerMojo(player2Stats)
    } yield if (player1Mojo > player2Mojo) pair._1 else pair._2

    winnerOpt match {
      case Some(winner) => ("response" -> winner.name) ~ ("latency" -> ChronoUnit.MILLIS.between(prior, Instant.now))
      case None => JNothing
    }
  }

  implicit val formats = DefaultFormats

  def playerStats(playerJs: JValue): PlayerStats = {
    val resultSetsJs = playerJs \ "response" \ "resultSets"

    val rowSetJs = resultSetsJs(1) \ "rowSet"

    val statsJs = rowSetJs(0)

    val points = statsJs(3).extractOpt[Double]

    val assists = statsJs(4).extractOpt[Double]

    val rebounds = statsJs(5).extractOpt[Double]

    PlayerStats(points, assists, rebounds)
  }
}

object Tournament {
  def winner(teams: List[Team]): Option[Team] = {
    /*
     *  TODO:
     *  1) Query stats.nba.com in order to get information about all teams
     *  2) Perform anaylitcs to determine the winner of the tournament
     */
    teams.sortBy(_.name) match {
      case firstTeam :: rest => {

        val winner = firstTeam
        Some(winner)
      }
      case _ => None
    }
  }
}

////////////////////////////////////////////////////////////////////

object StatsNBA {

  val statsNbaURL = "http://stats.nba.com/stats/"

  val teamRosterURI = "commonteamroster/?"

  val currentSeasonURI = "&Season=2015-16"

  lazy val teamNameToId = Map(
    "AtlantaHawks"          -> "1610612737",
    "BostonCeltics"         -> "1610612738",
    "BrooklynNets"          -> "1610612751",
    "CharlotteHornets"      -> "1610612766",
    "ChicagoBulls"          -> "1610612741",
    "ClevelandCavaliers"    -> "1610612739",
    "DallasMavericks"       -> "1610612742",
    "DenverNuggets"         -> "1610612743",
    "DetroitPistons"        -> "1610612765",
    "GoldenStateWarriors"   -> "1610612744",
    "Houston Rockets"       -> "1610612745",
    "IndianaPacers"         -> "1610612754",
    "LosAngelesClippers"    -> "1610612746",
    "LosAngelesLakers"      -> "1610612747",
    "MemphisGrizzlies"      -> "1610612763",
    "MiamiHeat"             -> "1610612748",
    "MilwaukeeBucks"        -> "1610612749",
    "MinnesotaTimberwolves" -> "1610612750",
    "NewOrleansPelicans"    -> "1610612740",
    "NewYorkKnicks"         -> "1610612752",
    "OklahomaCityThunder"   -> "1610612760",
    "OrlandoMagic"          -> "1610612753",
    "Philadelphia76ers"     -> "1610612755",
    "PhoenixSuns"           -> "1610612756",
    "PortlandTrailBlazers"  -> "1610612757",
    "SacramentoKings"       -> "1610612758",
    "SanAntonioSpurs"       -> "1610612759",
    "TorontoRaptors"        -> "1610612761",
    "UtahJazz"              -> "1610612762",
    "WashingtonWizards"     -> "1610612764"
  )

  val teams = teamNameToId.keys.toArray

  def teamId(teamName: String): Option[String] = teamNameToId.get(teamName)

  def teamIDURI(teamId: String) = s"TeamID=$teamId"

  def team(teamId: String): JValue = {

    val teamStatsNBAURL = statsNbaURL + teamRosterURI + teamIDURI(teamId) + currentSeasonURI

    val prior = Instant.now

    val response = parse(Source.fromURL(teamStatsNBAURL, StandardCharsets.UTF_8.name()).mkString)

    ("response" -> response) ~ ("latency" -> ChronoUnit.MILLIS.between(prior, Instant.now))
  }

  def team(t: Team): JValue = team(t.teamId)

  def teamByName(teamName: String): JValue =
    teamId(teamName).map(team(_))

  ///////////////////////////////////////////////////////////////

  val allPlayersURL = "http://stats.nba.com/stats/commonallplayers?LeagueID=00&Season=2015-16&IsOnlyCurrentSeason=1"

  implicit val formats = org.json4s.DefaultFormats

  // too much data to preload in advance -- make it lazy
  lazy val playerNameToId = 
    (parse( Source.fromURL(allPlayersURL).mkString ) \ "resultSets" \ "rowSet" )
      .children
      .map(en => (en(2).extract[String].replace(" ",""), en(0).extract[Int].toString))
      .toMap

  lazy val players = playerNameToId.keys.toArray

  def playerId(playerName: String): Option[String] = playerNameToId.get(playerName)

  def playerByName(playerName: String): JValue =
    playerId(playerName).map(player(_))

  def playerURI = "commonplayerinfo/?"

  def playerIDURI(playerId: String) = s"PlayerId=$playerId"

  def player(playerId: String): JValue = {
    val playerStatsNBAURL = statsNbaURL + playerURI + playerIDURI(playerId)

    val prior = Instant.now

    val response = parse(Source.fromURL(playerStatsNBAURL, StandardCharsets.UTF_8.name()).mkString)

    ("response" -> response) ~ ("latency" -> ChronoUnit.MILLIS.between(prior, Instant.now))
  }

  def player(p: Player): JValue = player(p.playerId)

  ///////////////////////

  def regularSeasonTypeURI = "&SeasonType=Regular+Season"

  def nbaLeagueURI = "&LeagueId=00"
}
