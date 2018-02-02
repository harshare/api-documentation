/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.apidocumentation.services

import javax.inject.Inject

import uk.gov.hmrc.apidocumentation.connectors.{ApiDefinitionConnector, ApiDocumentationConnector}
import uk.gov.hmrc.apidocumentation.models.{ApiDefinition, ExtendedApiDefinition, ExtendedApiVersion}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

class ApiDefinitionService @Inject()(apiDefinitionConnector: ApiDefinitionConnector, apiDocumentationConnector: ApiDocumentationConnector) {

  def fetchApiDefinitions(thirdPartyDeveloperEmail: Option[String])(implicit hc: HeaderCarrier): Future[Seq[ApiDefinition]] = {
    val localFuture = apiDefinitionConnector.fetchApiDefinitions(thirdPartyDeveloperEmail)
    val remoteFuture = apiDocumentationConnector.fetchApiDefinitions(thirdPartyDeveloperEmail)
    for {
      remoteDefinitions <- remoteFuture
      localDefinitions <- localFuture
    } yield (remoteDefinitions ++ localDefinitions.filterNot(_.isIn(remoteDefinitions))).sortBy(_.name)
  }


  def fetchApiDefinition(serviceName: String, thirdPartyDeveloperEmail: Option[String] = None)
                        (implicit hc: HeaderCarrier): Future[Option[ExtendedApiDefinition]] = {
    val localFuture = apiDefinitionConnector.fetchApiDefinition(serviceName, thirdPartyDeveloperEmail)
    val remoteFuture = apiDocumentationConnector.fetchApiDefinition(serviceName, thirdPartyDeveloperEmail)
    for {
      localDefinition <- localFuture
      remoteDefinition <- remoteFuture
    } yield combine(localDefinition, remoteDefinition)
  }

  private def combine(maybeLocalDefinition: Option[ExtendedApiDefinition], maybeRemoteDefinition: Option[ExtendedApiDefinition]) = {
    def findProductionDefinition(maybeLocalDefinition: Option[ExtendedApiDefinition], maybeRemoteDefinition: Option[ExtendedApiDefinition]) = {
      if(maybeLocalDefinition.exists(_.versions.exists(_.productionAvailability.isDefined))) maybeLocalDefinition
      else maybeRemoteDefinition
    }

    def findSandboxDefinition(maybeLocalDefinition: Option[ExtendedApiDefinition], maybeRemoteDefinition: Option[ExtendedApiDefinition]) = {
      if(maybeLocalDefinition.exists(_.versions.exists(_.sandboxAvailability.isDefined))) maybeLocalDefinition
      else maybeRemoteDefinition
    }

    def combineVersion(maybeProductionVersion: Option[ExtendedApiVersion], maybeSandboxVersion: Option[ExtendedApiVersion]) = {
      maybeProductionVersion.fold(maybeSandboxVersion){ productionVersion =>
        maybeSandboxVersion.fold(maybeProductionVersion){ sandboxVersion =>
          Some(sandboxVersion.copy(productionAvailability = productionVersion.productionAvailability))
        }
      }
    }

    def combineVersions(productionVersions: Seq[ExtendedApiVersion], sandboxVersions: Seq[ExtendedApiVersion]): Seq[ExtendedApiVersion] = {
      val allVersions = (productionVersions.map(_.version) ++ sandboxVersions.map(_.version)).distinct.sorted
      allVersions.flatMap { version =>
        combineVersion(productionVersions.find(_.version == version), sandboxVersions.find(_.version == version))
      }
    }

    val maybeProductionDefinition = findProductionDefinition(maybeLocalDefinition, maybeRemoteDefinition)
    val maybeSandboxDefinition = findSandboxDefinition(maybeLocalDefinition, maybeRemoteDefinition)

    maybeProductionDefinition.fold(maybeSandboxDefinition){ productionDefinition =>
      maybeSandboxDefinition.fold(maybeProductionDefinition){ sandboxDefinition =>
        Some(sandboxDefinition.copy(versions = combineVersions(productionDefinition.versions, sandboxDefinition.versions)))
      }
    }
  }
}
