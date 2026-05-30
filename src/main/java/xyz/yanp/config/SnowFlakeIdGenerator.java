package xyz.yanp.config;

import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.Configurable;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;
import xyz.yanp.util.SnowFlake;

import java.io.Serializable;
import java.util.Properties;

public class SnowFlakeIdGenerator implements IdentifierGenerator, Configurable {

    @Override
    public void configure(Type type, Properties params, ServiceRegistry serviceRegistry) throws MappingException {
    }

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) throws HibernateException {
        Serializable id = session.getEntityPersister(null, object).getClassMetadata().getIdentifier(object, session);

        //新增时有id,则不自动生成id
        if (id != null && String.valueOf(id).length() > 0) {
            return id;
        } else {
            return SnowFlake.getBean().nextId();
        }
    }
}
