package org.littleshoot.proxy;

import static org.jboss.netty.channel.Channels.pipeline;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating pipelines for incoming requests to our listening
 * socket.
 */
public class HttpServerPipelineFactory implements ChannelPipelineFactory {
    
    private final Logger log = 
        LoggerFactory.getLogger(HttpServerPipelineFactory.class);
    
    private final ProxyAuthorizationManager authenticationManager;
    private final ChannelGroup channelGroup;
    private final ChainProxyManager chainProxyManager;
    private final ProxyCacheManager cacheManager;
    //private final KeyStoreManager ksm;
    
    private final HandshakeHandlerFactory handshakeHandlerFactory;
    private int numHandlers;
    private final RelayPipelineFactoryFactory relayPipelineFactoryFactory;
    private final Timer timer;
    private final ClientSocketChannelFactory clientChannelFactory;
    
    /**
     * Creates a new pipeline factory with the specified class for processing
     * proxy authentication.
     * 
     * @param authorizationManager The manager for proxy authentication.
     * @param channelGroup The group that keeps track of open channels.
     * @param chainProxyManager upstream proxy server host and port or
     * <code>null</code> if none used.
     * @param ksm The KeyStore manager.
     * @param relayPipelineFactoryFactory The relay pipeline factory factory.
     * @param timer The global timer for timing out idle connections. 
     * @param clientChannelFactory The factory for creating outgoing channels
     * to external sites.
     */
    public HttpServerPipelineFactory(
        final ProxyAuthorizationManager authorizationManager, 
        final ChannelGroup channelGroup, 
        final ChainProxyManager chainProxyManager, 
        final HandshakeHandlerFactory handshakeHandlerFactory,
        final RelayPipelineFactoryFactory relayPipelineFactoryFactory, 
        final Timer timer, final ClientSocketChannelFactory clientChannelFactory) {
        this(authorizationManager, channelGroup, chainProxyManager, 
                handshakeHandlerFactory, 
                relayPipelineFactoryFactory, timer, clientChannelFactory, 
                ProxyUtils.loadCacheManager());
    }
    
    /**
     * Creates a new pipeline factory with the specified class for processing
     * proxy authentication.
     * 
     * @param authorizationManager The manager for proxy authentication.
     * @param channelGroup The group that keeps track of open channels.
     * @param chainProxyManager upstream proxy server host and port or
     * <code>null</code> if none used.
     * @param ksm The KeyStore manager.
     * @param relayPipelineFactoryFactory The relay pipeline factory factory.
     * @param timer The global timer for timing out idle connections. 
     * @param clientChannelFactory The factory for creating outgoing channels
     * to external sites.
     */
    public HttpServerPipelineFactory(
        final ProxyAuthorizationManager authorizationManager, 
        final ChannelGroup channelGroup, 
        final ChainProxyManager chainProxyManager, 
        final HandshakeHandlerFactory handshakeHandlerFactory,
        final RelayPipelineFactoryFactory relayPipelineFactoryFactory, 
        final Timer timer, final ClientSocketChannelFactory clientChannelFactory,
        final ProxyCacheManager proxyCacheManager) {
        
        this.handshakeHandlerFactory = handshakeHandlerFactory;
        this.relayPipelineFactoryFactory = relayPipelineFactoryFactory;
        this.timer = timer;
        this.clientChannelFactory = clientChannelFactory;
        
        log.debug("Creating server with handshake handler: {}", 
                handshakeHandlerFactory);
        this.authenticationManager = authorizationManager;
        this.channelGroup = channelGroup;
        this.chainProxyManager = chainProxyManager;
        //this.ksm = ksm;
        this.cacheManager = proxyCacheManager;        
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        final ChannelPipeline pipeline = pipeline();

        log.debug("Accessing pipeline");
        if (this.handshakeHandlerFactory != null) {
            log.debug("Adding SSL handler");
            //final SslContextFactory scf = new SslContextFactory(this.ksm);
            //final SSLEngine engine = scf.getServerContext().createSSLEngine();
            //engine.setUseClientMode(false);
            //pipeline.addLast("ssl", new SslHandler(engine));
            final HandshakeHandler hh = 
                this.handshakeHandlerFactory.newHandshakeHandler();
            pipeline.addLast(hh.getId(), hh.getChannelHandler());
        }
            
        // We want to allow longer request lines, headers, and chunks 
        // respectively.
        pipeline.addLast("decoder", 
            new HttpRequestDecoder(8192, 8192*2, 8192*2));
        pipeline.addLast("encoder", new ProxyHttpResponseEncoder(cacheManager));
        
        final HttpRequestHandler httpRequestHandler = 
            new HttpRequestHandler(this.cacheManager, authenticationManager,
            this.channelGroup, this.chainProxyManager, 
            relayPipelineFactoryFactory, this.clientChannelFactory);
        
        pipeline.addLast("idle", new IdleStateHandler(this.timer, 0, 0, 70));
        //pipeline.addLast("idleAware", new IdleAwareHandler("Client-Pipeline"));
        pipeline.addLast("idleAware", new IdleRequestHandler(httpRequestHandler));
        pipeline.addLast("handler", httpRequestHandler);
        this.numHandlers++;
        return pipeline;
    }
}
