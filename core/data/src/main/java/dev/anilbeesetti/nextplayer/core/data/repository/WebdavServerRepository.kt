package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.data.remote.CredentialEncryptor
import dev.anilbeesetti.nextplayer.core.database.dao.WebdavServerDao
import dev.anilbeesetti.nextplayer.core.database.entities.WebdavServerEntity
import dev.anilbeesetti.nextplayer.core.model.WebdavServer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebdavServerRepository @Inject constructor(
    private val dao: WebdavServerDao,
    private val encryptor: CredentialEncryptor,
) {

    fun getAllServers(): Flow<List<WebdavServer>> =
        dao.getAllServers().map { entities -> entities.map { it.toDomain() } }

    suspend fun saveServer(server: WebdavServer): Long =
        dao.insertServer(server.toEntity())

    suspend fun updateServer(server: WebdavServer) =
        dao.updateServer(server.toEntity())

    suspend fun deleteServerById(id: Long) =
        dao.deleteServerById(id)

    suspend fun getServerById(id: Long): WebdavServer? =
        dao.getServerById(id)?.toDomain()

    suspend fun reorderServers(ids: List<Long>) {
        ids.forEachIndexed { index, id ->
            dao.updatePosition(id, index)
        }
    }

    private fun WebdavServerEntity.toDomain() = WebdavServer(
        id = id,
        name = name,
        host = host,
        port = port,
        path = path,
        username = username,
        password = encryptor.decrypt(password),
        useSsl = useSsl,
        allowSelfSigned = allowSelfSigned,
        createdAt = createdAt,
        updatedAt = updatedAt,
        position = position
    )

    private fun WebdavServer.toEntity() = WebdavServerEntity(
        id = id,
        name = name,
        host = host,
        port = port,
        path = path,
        username = username,
        password = encryptor.encrypt(password),
        useSsl = useSsl,
        allowSelfSigned = allowSelfSigned,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
        position = position
    )
}
