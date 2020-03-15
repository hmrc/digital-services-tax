package uk.gov.hmrc.digitalservicestax.util


import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.{BaseOneAppPerSuite, FakeApplicationFactory, PlaySpec}
import play.api.i18n.MessagesApi
import play.api.inject.DefaultApplicationLifecycle
import play.api.libs.ws.WSClient
import play.api.{Application, ApplicationLoader}
import play.core.DefaultWebCommands
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpClient, HttpClient}

import scala.collection.mutable
import scala.concurrent.{Future, ExecutionContext => EC}

trait FakeApplicationSpec
  extends PlaySpec with BaseOneAppPerSuite with FakeApplicationFactory with TestWiring with MockitoSugar {
  protected val context = ApplicationLoader.Context(
    environment,
    sourceMapper = None,
    new DefaultWebCommands,
    configuration,
    new DefaultApplicationLifecycle
  )

  lazy val messagesApi = app.injector.instanceOf[MessagesApi]
  lazy val wsClient = app.injector.instanceOf[WSClient]
  lazy val httpClient: HttpClient = new DefaultHttpClient(configuration, httpAuditing, wsClient, actorSystem)

  override def fakeApplication(): Application =
    new SdilApplicationLoader().load(context)

  lazy val testPersistence: SdilPersistence = new SdilPersistence {

    val returns: DAO[String, ReturnPeriod, SdilReturn] = new DAO[String, ReturnPeriod, SdilReturn] {
      private var data: Map[(String, ReturnPeriod), SdilReturn] = Map.empty
      private var getData: Map[(String, ReturnPeriod), (SdilReturn, Option[BSONObjectID])] = Map.empty
      def update(user: String, period: ReturnPeriod, value: SdilReturn)(implicit ec: EC): Future[Unit] = {
        data = data + { (user, period) -> value }
        Future.successful(())
      }

      def dropCollection(implicit ec: EC): Future[Boolean] = {
        data = Map.empty
        Future.successful(data.isEmpty)
      }

      def get(
        user: String,
        key: ReturnPeriod
      )(implicit ec: EC): Future[Option[(SdilReturn, Option[BSONObjectID])]] =
        Future.successful(getData.get((user, key)))

      def list(user: String)(implicit ec: EC): Future[Map[ReturnPeriod, SdilReturn]] =
        Future.successful {
          data.toList.collect { case ((`user`, period), ret) => (period, ret) }.toMap
        }
      def listVariable(user: String)(implicit ec: EC): Future[Map[ReturnPeriod, SdilReturn]] =
        Future.successful {
          data.toList.collect { case ((`user`, period), ret) => (period, ret) }.toMap
        }
    }

    val subscriptions: SubsDAO[String, Subscription] = new SubsDAO[String, Subscription] {
      private var data: scala.collection.mutable.Map[String, List[Subscription]] = mutable.Map.empty

      override def insert(key: String, value: Subscription)(implicit ec: EC): Future[Unit] = {
        data += data.get(key).fold(key -> List(value))(x => key -> (value +: x))
        Future.successful(())
      }

      def dropCollection(implicit ec: EC): Future[Boolean] = {
        data = scala.collection.mutable.Map.empty[String, List[Subscription]]
        Future.successful(data.isEmpty)
      }

      override def list(key: String)(implicit ec: EC): Future[List[Subscription]] =
        Future(data.getOrElse(key, List.empty[Subscription]))
    }
  }
}
