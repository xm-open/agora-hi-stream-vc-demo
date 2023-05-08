Pod::Spec.new do |s|
    s.name             = 'Extensions'
    s.version          = '0.1.0'
    s.summary          = 'ByteDance effect plugin for Agora RTE extensions.'
    s.description      = 'project.description'
    s.homepage         = 'https://github.com/AgoraIO-Community/AgoraMarketPlace'
    s.author           = { 'Agora' => 'developer@agora.io' }
    s.source           = { :path => '.' }
	s.vendored_frameworks = 'agora-hi-stream-vc.xcframework', 'agora-hi-stream-vc-deps.xcframework'
	# s.vendored_frameworks = '*.xcframework'
	s.requires_arc     = true
    s.platform         = :ios, '11.0'
  end
  
