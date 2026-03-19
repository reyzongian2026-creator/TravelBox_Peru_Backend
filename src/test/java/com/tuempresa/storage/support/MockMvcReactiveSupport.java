package com.tuempresa.storage.support;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

public final class MockMvcReactiveSupport {

    private MockMvcReactiveSupport() {
    }

    public static ResultActions perform(MockMvc mockMvc, RequestBuilder requestBuilder) throws Exception {
        ResultActions initialCall = mockMvc.perform(requestBuilder);
        MvcResult result = initialCall.andReturn();
        if (result.getRequest().isAsyncStarted()) {
            result.getAsyncResult();
            return mockMvc.perform(asyncDispatch(result));
        }
        return initialCall;
    }
}
